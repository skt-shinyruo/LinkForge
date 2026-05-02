package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;

public record LinkDto(
        long id,
        long tenantId,
        Long applicationId,
        Long domainId,
        String lifecycleState,
        String code,
        String shortUrl,
        String originalUrl,
        String note,
        boolean enabled,
        Instant expiresAt,
        Instant archivedAt,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        List<String> tags,
        Instant createdAt
) {
}
