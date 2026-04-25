package com.linkforge.contract.redirect;

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
        String queryForwardAllowlist,
        String hostname,
        Long applicationId,
        Long domainId,
        String lifecycleState
) {

    public static final String ACTIVE_LIFECYCLE_STATE = "ACTIVE";

    public LinkMeta {
        lifecycleState = normalizeLifecycleState(lifecycleState);
    }

    public LinkMeta(
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
            String queryForwardAllowlist,
            String hostname,
            Long applicationId,
            Long domainId
    ) {
        this(
                id,
                tenantId,
                code,
                originalUrl,
                enabled,
                expiresAt,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                hostname,
                applicationId,
                domainId,
                ACTIVE_LIFECYCLE_STATE
        );
    }

    public LinkMeta(
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
            String queryForwardAllowlist,
            String hostname
    ) {
        this(
                id,
                tenantId,
                code,
                originalUrl,
                enabled,
                expiresAt,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                hostname,
                null,
                null,
                ACTIVE_LIFECYCLE_STATE
        );
    }

    public boolean activeLifecycle() {
        return ACTIVE_LIFECYCLE_STATE.equals(lifecycleState);
    }

    private static String normalizeLifecycleState(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return ACTIVE_LIFECYCLE_STATE;
        }
        return raw.trim().toUpperCase();
    }
}
