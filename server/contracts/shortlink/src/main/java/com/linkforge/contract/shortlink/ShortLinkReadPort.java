package com.linkforge.contract.shortlink;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ShortLinkReadPort {

    Optional<RedirectLinkView> findRedirectMetaByHostAndCode(String host, String code);

    Optional<ShortLinkOwnership> findOwnership(long tenantId, long linkId);

    Map<Long, ShortLinkSummary> listSummaries(long tenantId, List<Long> linkIds);

    record RedirectLinkView(
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

        public RedirectLinkView {
            lifecycleState = normalizeLifecycleState(lifecycleState);
        }

        public RedirectLinkView(
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

    record ShortLinkOwnership(Long applicationId, Long domainId) {
    }

    record ShortLinkSummary(long linkId, String code, String shortUrl, String originalUrl, boolean deleted) {
        public ShortLinkSummary(long linkId, String code, String originalUrl, boolean deleted) {
            this(linkId, code, null, originalUrl, deleted);
        }
    }
}
