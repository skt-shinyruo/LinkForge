package com.linkforge.contract.shortlink;

import java.time.Instant;
import java.util.List;

public record ShortLinkPublicSnapshot(
        long tenantId,
        long linkId,
        String code,
        String hostname,
        String originalUrl,
        boolean enabled,
        Instant expiresAtUtc,
        Integer redirectStatusCode,
        boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        Instant archivedAtUtc,
        Long applicationId,
        Long domainId
) {
}
