package com.linkforge.analytics.domain;

import java.time.Instant;
import java.util.Objects;

public record VisitFact(
        long tenantId,
        long linkId,
        Instant occurredAt,
        Long applicationId,
        Long domainId,
        String code,
        String originalUrl,
        VisitDimension dimension
) {

    public VisitFact {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (linkId <= 0) {
            throw new IllegalArgumentException("linkId must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must be provided");
        dimension = dimension == null ? VisitDimension.empty() : dimension;
    }

    public static VisitFact create(
            long tenantId,
            long linkId,
            Instant occurredAt,
            Long applicationId,
            Long domainId,
            String code,
            String originalUrl,
            VisitDimension dimension
    ) {
        return new VisitFact(
                tenantId,
                linkId,
                occurredAt,
                applicationId,
                domainId,
                trimToNull(code),
                trimToNull(originalUrl),
                dimension
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
