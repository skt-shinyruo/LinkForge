package com.linkforge.shortlink.interfaces.web.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ShortLinkUpdateHttpRequest(
        @Size(max = 2048, message = "URL 过长")
        String originalUrl,
        @Size(max = 512, message = "备注过长")
        String note,
        Instant expiresAt,
        Boolean clearExpiresAt,
        Boolean enabled,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean clearRedirectStatusCode,
        Boolean previewEnabled,
        @Size(max = 2048, message = "落地页 URL 过长")
        String unavailableLandingUrl,
        @Size(max = 16, message = "queryForwardMode 过长")
        String queryForwardMode,
        Boolean clearQueryForwardMode,
        List<@Size(max = 64, message = "queryForwardAllowlist 项过长") String> queryForwardAllowlist,
        @Size(max = 32, message = "lifecycleState 过长")
        String lifecycleState
) {
}
