package com.linkforge.redirect.service;

import java.time.LocalDateTime;

public record LinkMeta(
        long id,
        long tenantId,
        String code,
        String originalUrl,
        boolean enabled,
        LocalDateTime expiresAt,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        String queryForwardAllowlist
) {
}
