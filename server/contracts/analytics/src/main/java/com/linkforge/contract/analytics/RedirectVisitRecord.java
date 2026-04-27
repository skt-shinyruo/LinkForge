package com.linkforge.contract.analytics;

public record RedirectVisitRecord(
        long tenantId,
        long linkId,
        long occurredAtMillis,
        Long applicationId,
        Long domainId,
        String code,
        String originalUrl,
        VisitContext visitContext
) {
}
