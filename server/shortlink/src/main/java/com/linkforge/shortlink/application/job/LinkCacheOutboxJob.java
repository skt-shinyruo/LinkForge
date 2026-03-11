package com.linkforge.shortlink.application.job;

import com.linkforge.shortlink.infrastructure.outbox.LinkCacheOutboxRepository;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LinkCacheOutboxJob {

    private static final Logger log = LoggerFactory.getLogger(LinkCacheOutboxJob.class);

    private final LinkCacheOutboxRepository outbox;
    private final ShortLinkQueryMapper shortLinkQueryMapper;
    private final LinkCachePort linkCache;
    private final int batchSize;
    private final Counter processedDone;
    private final Counter processedRetry;
    private final Counter processedError;

    public LinkCacheOutboxJob(
            LinkCacheOutboxRepository outbox,
            ShortLinkQueryMapper shortLinkQueryMapper,
            LinkCachePort linkCache,
            MeterRegistry meterRegistry,
            @Value("${APP_LINK_CACHE_OUTBOX_BATCH_SIZE:200}") int batchSize
    ) {
        this.outbox = outbox;
        this.shortLinkQueryMapper = shortLinkQueryMapper;
        this.linkCache = linkCache;
        this.batchSize = batchSize;
        this.processedDone = Counter.builder("linkforge.shortlink.cache_outbox.drain.processed")
                .tag("result", "done")
                .description("outbox drain processed items")
                .register(meterRegistry);
        this.processedRetry = Counter.builder("linkforge.shortlink.cache_outbox.drain.processed")
                .tag("result", "retry")
                .description("outbox drain processed items")
                .register(meterRegistry);
        this.processedError = Counter.builder("linkforge.shortlink.cache_outbox.drain.processed")
                .tag("result", "error")
                .description("outbox drain processed items")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${APP_LINK_CACHE_OUTBOX_DELAY_MS:1000}")
    @SchedulerLock(name = "lf:job:link-cache-outbox", lockAtMostFor = "PT2M")
    public void drain() {
        int limit = normalizeBatchSize(batchSize);
        List<LinkCacheOutboxRepository.PendingItem> items = outbox.listPending(limit);
        if (items.isEmpty()) {
            return;
        }
        for (LinkCacheOutboxRepository.PendingItem item : items) {
            processOne(item);
        }
    }

    void processOne(LinkCacheOutboxRepository.PendingItem item) {
        String code = item == null ? null : item.code();
        if (code == null || code.isBlank()) {
            return;
        }

        try {
            ShortLinkEntity e = shortLinkQueryMapper.findByCode(code);
            boolean ok;
            if (e == null || e.getArchivedAt() != null) {
                ok = linkCache.tryEvict(code);
            } else {
                ok = linkCache.tryPut(toMeta(e));
            }

            if (ok) {
                outbox.markDone(code);
                processedDone.increment();
            } else {
                int attempts = safeAttempts(item.attempts()) + 1;
                long delay = backoffSeconds(attempts);
                outbox.markRetry(code, attempts, "cache write failed", delay);
                processedRetry.increment();
            }
        } catch (Exception ex) {
            int attempts = safeAttempts(item.attempts()) + 1;
            long delay = backoffSeconds(attempts);
            outbox.markRetry(code, attempts, ex.getMessage(), delay);
            processedError.increment();
            log.debug("link cache outbox process failed: code={}, attempts={}, err={}", code, attempts, ex.getMessage());
        }
    }

    private static LinkMeta toMeta(ShortLinkEntity e) {
        return new LinkMeta(
                e.getId(),
                e.getTenantId(),
                e.getCode(),
                e.getOriginalUrl(),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getExpiresAt(),
                e.getRedirectStatusCode(),
                Boolean.TRUE.equals(e.getPreviewEnabled()),
                e.getUnavailableLandingUrl(),
                e.getQueryForwardMode(),
                e.getQueryForwardAllowlist()
        );
    }

    private static int normalizeBatchSize(int raw) {
        if (raw <= 0) {
            return 200;
        }
        return Math.max(1, Math.min(raw, 1000));
    }

    private static int safeAttempts(int attempts) {
        return Math.max(attempts, 0);
    }

    /**
     * 简单指数退避：1s,2s,4s... 最大 300s。
     */
    private static long backoffSeconds(int attempts) {
        if (attempts <= 0) {
            return 1;
        }
        int exp = Math.min(Math.max(attempts - 1, 0), 8);
        long delay = 1L << exp; // 2^exp
        return Math.min(delay, 300);
    }
}
