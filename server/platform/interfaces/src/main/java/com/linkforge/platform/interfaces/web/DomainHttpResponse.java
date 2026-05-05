package com.linkforge.platform.interfaces.web;

public record DomainHttpResponse(
        long id,
        long tenantId,
        Long applicationId,
        String hostname,
        String scope
) {
}
