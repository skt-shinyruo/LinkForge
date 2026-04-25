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
            Long domainId,
            String lifecycleState
    ) {
        private static final String ACTIVE_LIFECYCLE_STATE = "ACTIVE";

        public RedirectLinkMeta {
            lifecycleState = normalizeLifecycleState(lifecycleState);
        }

        public RedirectLinkMeta(
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
            this(
                    tenantId,
                    linkId,
                    code,
                    hostname,
                    originalUrl,
                    enabled,
                    expiresAtUtc,
                    redirectStatusCode,
                    previewEnabled,
                    unavailableLandingUrl,
                    queryForwardMode,
                    queryForwardAllowlist,
                    applicationId,
                    domainId,
                    ACTIVE_LIFECYCLE_STATE
            );
        }

        private static String normalizeLifecycleState(String raw) {
            if (raw == null || raw.trim().isBlank()) {
                return ACTIVE_LIFECYCLE_STATE;
            }
            return raw.trim().toUpperCase();
        }
    }

    record LinkOwnership(Long applicationId, Long domainId) {
    }

    record LinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
    }
}
