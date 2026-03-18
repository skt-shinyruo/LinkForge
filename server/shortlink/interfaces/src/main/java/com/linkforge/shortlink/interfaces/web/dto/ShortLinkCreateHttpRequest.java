package com.linkforge.shortlink.interfaces.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ShortLinkCreateHttpRequest(
        @NotBlank(message = "originalUrl 不能为空")
        @Size(max = 2048, message = "URL 过长")
        String originalUrl,
        @Size(max = 512, message = "备注过长")
        String note,
        Instant expiresAt,
        Boolean enabled,
        @Size(max = 32, message = "自定义短码过长")
        String customCode,
        Set<String> tags,
        Integer redirectStatusCode,
        Boolean previewEnabled,
        @Size(max = 2048, message = "落地页 URL 过长")
        String unavailableLandingUrl,
        @Size(max = 16, message = "queryForwardMode 过长")
        String queryForwardMode,
        List<@Size(max = 64, message = "queryForwardAllowlist 项过长") String> queryForwardAllowlist
) {
}
