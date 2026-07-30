package com.linkforge.shortlink.infrastructure.redirect;

/**
 * 一条到期的缓存失效任务；{@code attempts} 是此前失败次数，下一次失败时由任务递增。
 */
public record RedirectCacheInvalidationOutboxRow(
        long id,
        long tenantId,
        Long domainId,
        String code,
        int attempts
) {
}
