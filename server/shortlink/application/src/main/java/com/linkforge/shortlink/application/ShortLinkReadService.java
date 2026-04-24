package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.Optional;

public interface ShortLinkReadService {

    Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    record RedirectLinkMeta(
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
            String queryForwardAllowlist,
            Long applicationId,
            Long domainId
    ) {
    }
}
