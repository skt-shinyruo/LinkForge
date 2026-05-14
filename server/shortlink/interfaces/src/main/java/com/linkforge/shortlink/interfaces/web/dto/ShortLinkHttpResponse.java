package com.linkforge.shortlink.interfaces.web.dto;

import java.time.Instant;
import java.util.List;

public record ShortLinkHttpResponse(
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
        Instant createdAt,
        boolean pendingApproval,
        Long approvalRequestId,
        String requestedOriginalUrl
) {
    public ShortLinkHttpResponse(
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
        this(
                id,
                tenantId,
                applicationId,
                domainId,
                lifecycleState,
                code,
                shortUrl,
                originalUrl,
                note,
                enabled,
                expiresAt,
                archivedAt,
                redirectStatusCode,
                previewEnabled,
                unavailableLandingUrl,
                queryForwardMode,
                queryForwardAllowlist,
                tags,
                createdAt,
                false,
                null,
                null
        );
    }
}
