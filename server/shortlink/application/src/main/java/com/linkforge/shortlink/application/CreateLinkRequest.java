package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateLinkRequest(
        String originalUrl,
        String note,
        Instant expiresAt,
        Boolean enabled,
        String customCode,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        List<String> queryForwardAllowlist,
        Long applicationId,
        Long domainId,
        String lifecycleState
) {
}
