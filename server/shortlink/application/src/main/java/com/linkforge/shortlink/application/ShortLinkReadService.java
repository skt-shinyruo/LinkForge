package com.linkforge.shortlink.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadService {

    Optional<RedirectLinkMeta> findRedirectMetaByHostAndCode(String host, String code);

    Optional<LinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, LinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    List<Long> listLinkIdsByApplication(long tenantId, long applicationId);

    List<Long> listLinkIdsByDomain(long tenantId, long domainId);

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

    record LinkOwnership(Long applicationId, Long domainId) {
    }

    record LinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
    }
}
