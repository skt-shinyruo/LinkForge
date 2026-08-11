package com.linkforge.shortlink.infrastructure.redirect;

import java.time.LocalDateTime;

/** 待处理缓存失效 outbox 的总量和最早入队时间。 */
public record RedirectCacheInvalidationOutboxStats(long pendingCount, LocalDateTime oldestCreatedAtUtc) {
}
