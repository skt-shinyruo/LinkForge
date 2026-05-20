package com.linkforge.shortlink.infrastructure.redirect;

public record RedirectCacheInvalidationOutboxRow(
        long id,
        long tenantId,
        Long domainId,
        String code,
        int attempts
) {
}
