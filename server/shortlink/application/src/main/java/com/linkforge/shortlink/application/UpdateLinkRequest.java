package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record UpdateLinkRequest(
        String originalUrl,
        String note,
        Instant expiresAt,
        Boolean clearExpiresAt,
        Boolean enabled,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean clearRedirectStatusCode,
        Boolean previewEnabled,
        String unavailableLandingUrl,
        String queryForwardMode,
        Boolean clearQueryForwardMode,
        List<String> queryForwardAllowlist,
        String lifecycleState
) {
}
