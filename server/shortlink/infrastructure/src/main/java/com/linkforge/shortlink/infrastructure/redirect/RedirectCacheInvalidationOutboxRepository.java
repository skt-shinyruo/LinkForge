package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.infrastructure.persistence.mapper.RedirectCacheInvalidationOutboxMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class RedirectCacheInvalidationOutboxRepository implements RedirectCacheInvalidationOutboxPort {

    static final long UNSCOPED_DOMAIN = 0L;

    private final RedirectCacheInvalidationOutboxMapper mapper;
    private final Clock clock;

    public RedirectCacheInvalidationOutboxRepository(RedirectCacheInvalidationOutboxMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

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

    public List<RedirectCacheInvalidationOutboxRow> listDue(LocalDateTime nowUtc, int limit) {
        return mapper.listDue(nowUtc, limit);
    }

    public void markProcessed(long id, LocalDateTime processedAtUtc) {
        mapper.markProcessed(id, processedAtUtc);
    }

    public void markFailed(long id, int attempts, String lastError, LocalDateTime nextAttemptAtUtc) {
        mapper.markFailed(id, attempts, lastError, nextAttemptAtUtc);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static long domainScope(Long domainId) {
        return domainId == null ? UNSCOPED_DOMAIN : domainId;
    }
}
