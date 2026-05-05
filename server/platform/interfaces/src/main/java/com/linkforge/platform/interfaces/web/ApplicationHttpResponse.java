package com.linkforge.platform.interfaces.web;

public record ApplicationHttpResponse(
        long id,
        long tenantId,
        String applicationKey,
        String displayName
) {
}
