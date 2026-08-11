package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.foundation.observability.OperationalMetrics;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 重试短链跳转缓存失效的持久化 outbox 任务。
 *
 * <p>应用事务先持久化待失效项，提交后再做一次低延迟的 best-effort 删除；无论快路径是否成功，本任务都会
 * 扫描到期记录并重复执行删除，成功后才标记完成。因此处理语义至少一次，缓存删除必须保持幂等。</p>
 *
 * <p>ShedLock 用于避免多个应用实例同时跑批；查询本身不领取或锁定行，绕过调度锁并发调用时仍可能重复处理。
 * 单次调度限制批次数和每批行数，失败记录采用指数退避且不设最大尝试次数，避免暂时性 Redis/域名查询故障
 * 丢失最终失效机会。</p>
 */
@Component
public class RedirectCacheInvalidationOutboxJob {

    static final int BATCH_LIMIT = 100;
    private static final int MAX_BATCHES = 10;
    private static final int MAX_ERROR_LENGTH = 512;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(RedirectCacheInvalidationOutboxJob.class);

    private final RedirectCacheInvalidationOutboxRepository outbox;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final Clock clock;
    private final OperationalMetrics metrics;

    public RedirectCacheInvalidationOutboxJob(
            RedirectCacheInvalidationOutboxRepository outbox,
            RedirectCacheSyncPort redirectCacheSync,
            Clock clock
    ) {
        this(outbox, redirectCacheSync, clock, OperationalMetrics.noop());
    }

    @Autowired
    public RedirectCacheInvalidationOutboxJob(
            RedirectCacheInvalidationOutboxRepository outbox,
            RedirectCacheSyncPort redirectCacheSync,
            Clock clock,
            OperationalMetrics metrics
    ) {
        this.outbox = outbox;
        this.redirectCacheSync = redirectCacheSync;
        this.clock = clock;
        this.metrics = metrics == null ? OperationalMetrics.noop() : metrics;
    }

    /**
     * 分批处理当前到期记录；每轮最多处理 {@code BATCH_LIMIT * MAX_BATCHES} 条。
     */
    @Scheduled(fixedDelayString = "${APP_SHORTLINK_REDIRECT_CACHE_INVALIDATION_OUTBOX_DELAY_MS:1000}")
    @SchedulerLock(name = "lf:job:shortlink:redirect-cache-invalidation-outbox", lockAtMostFor = "PT2M")
    public void process() {
        observeBacklog();
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int n = processOnce();
            total += n;
            if (n <= 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("redirect cache invalidation outbox processed: count={}", total);
        }
    }

    /**
     * 执行一批缓存失效并分别记录成功或下次重试时间。
     *
     * <p>一条记录失败不会阻断同批其他记录。返回值是已尝试处理的非空行数，而不是成功数，供外层判断是否
     * 继续拉取下一批。</p>
     */
    int processOnce() {
        LocalDateTime nowUtc = nowUtc();
        List<RedirectCacheInvalidationOutboxRow> rows = outbox.listDue(nowUtc, BATCH_LIMIT);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int handled = 0;
        for (RedirectCacheInvalidationOutboxRow row : rows) {
            if (row == null) {
                continue;
            }
            try {
                redirectCacheSync.evict(row.tenantId(), row.domainId(), row.code());
                outbox.markProcessed(row.id(), nowUtc());
                metrics.increment("linkforge.outbox.events", "outbox", "redirect_cache_invalidation", "result", "success");
            } catch (RuntimeException ex) {
                metrics.increment("linkforge.outbox.events", "outbox", "redirect_cache_invalidation", "result", "retry");
                int attempts = row.attempts() + 1;
                outbox.markFailed(
                        row.id(),
                        attempts,
                        truncate(errorMessage(ex), MAX_ERROR_LENGTH),
                        nextAttemptAt(attempts)
                );
            }
            handled++;
        }
        return handled;
    }

    private void observeBacklog() {
        try {
            RedirectCacheInvalidationOutboxStats stats = outbox.pendingStats();
            long pending = stats == null ? 0L : Math.max(stats.pendingCount(), 0L);
            metrics.set("linkforge.outbox.pending", pending, "outbox", "redirect_cache_invalidation");
            long ageSeconds = 0L;
            if (stats != null && stats.oldestCreatedAtUtc() != null) {
                ageSeconds = Math.max(Duration.between(stats.oldestCreatedAtUtc(), nowUtc()).toSeconds(), 0L);
            }
            metrics.set("linkforge.outbox.oldest_age_seconds", ageSeconds, "outbox", "redirect_cache_invalidation");
        } catch (RuntimeException ex) {
            metrics.increment("linkforge.outbox.failures", "outbox", "redirect_cache_invalidation", "stage", "observe");
            log.debug("redirect cache invalidation outbox metrics failed: err={}", ex.getMessage());
        }
    }

    private LocalDateTime nextAttemptAt(int attempts) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(Math.max(attempts - 1, 0), 8));
        return nowUtc().plusSeconds(seconds);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getName() : message;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
