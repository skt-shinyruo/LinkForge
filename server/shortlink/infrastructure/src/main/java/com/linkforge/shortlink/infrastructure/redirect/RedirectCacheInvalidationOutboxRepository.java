package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.RedirectCacheInvalidationOutboxMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 跳转缓存失效 outbox 的 MyBatis 适配器。
 *
 * <p>{@link #enqueue(long, Long, String)} 通常参与短链写事务，保证状态变更与失效意图原子提交。
 * 数据库唯一键以 {@code tenantId + domainScope + code} 合并重复意图，其中无域名短链用 {@code 0}
 * 归一化；重复入队会原子递增 generation、把既有记录重新置为待处理，并清空错误与重试计数。</p>
 */
@Component
public class RedirectCacheInvalidationOutboxRepository implements RedirectCacheInvalidationOutboxPort {

    static final long UNSCOPED_DOMAIN = 0L;

    private final RedirectCacheInvalidationOutboxMapper mapper;
    private final Clock clock;

    public RedirectCacheInvalidationOutboxRepository(RedirectCacheInvalidationOutboxMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 在当前事务中登记缓存失效意图。
     *
     * @throws IllegalArgumentException 租户非法或短码为空时抛出
     */
    @Override
    public void enqueue(long tenantId, Long domainId, String code) {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        mapper.enqueue(
                tenantId,
                domainId,
                domainScope(domainId),
                code,
                nowUtc()
        );
    }

    /**
     * 按到期时间和主键顺序读取待处理项；该查询不占有任务，调用方依赖调度锁避免并发领取。
     */
    public List<RedirectCacheInvalidationOutboxRow> listDue(LocalDateTime nowUtc, int limit) {
        return mapper.listDue(nowUtc, limit);
    }

    public RedirectCacheInvalidationOutboxStats pendingStats() {
        return mapper.pendingStats();
    }

    /** 仅当记录仍是 worker 读取到的 {@code PENDING} generation 时标记完成。 */
    public boolean markProcessed(long id, long generation, LocalDateTime processedAtUtc) {
        return mapper.markProcessed(id, generation, processedAtUtc) > 0;
    }

    /** 仅当记录仍是 worker 读取到的 {@code PENDING} generation 时保存重试状态。 */
    public boolean markFailed(
            long id,
            long generation,
            int attempts,
            String lastError,
            LocalDateTime nextAttemptAtUtc
    ) {
        return mapper.markFailed(id, generation, attempts, lastError, nextAttemptAtUtc) > 0;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static long domainScope(Long domainId) {
        return domainId == null ? UNSCOPED_DOMAIN : domainId;
    }
}
