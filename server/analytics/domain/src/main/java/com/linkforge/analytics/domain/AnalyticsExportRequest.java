package com.linkforge.analytics.domain;

import java.time.Instant;
import java.util.Objects;

public record AnalyticsExportRequest(
        long tenantId,
        long linkId,
        Long applicationId,
        AggregationWindow window,
        long requestedByUserId,
        Instant requestedAt,
        Status status
) {

    public AnalyticsExportRequest {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (linkId <= 0) {
            throw new IllegalArgumentException("linkId must be positive");
        }
        if (requestedByUserId <= 0) {
            throw new IllegalArgumentException("requestedByUserId must be positive");
        }
        Objects.requireNonNull(window, "window must be provided");
        Objects.requireNonNull(requestedAt, "requestedAt must be provided");
        status = status == null ? Status.PENDING_APPROVAL : status;
    }

    public static AnalyticsExportRequest request(
            long tenantId,
            long linkId,
            Long applicationId,
            AggregationWindow window,
            long requestedByUserId,
            Instant requestedAt
    ) {
        return new AnalyticsExportRequest(
                tenantId,
                linkId,
                applicationId,
                window,
                requestedByUserId,
                requestedAt,
                Status.PENDING_APPROVAL
        );
    }

    public AnalyticsExportRequest approve() {
        if (status != Status.PENDING_APPROVAL) {
            throw new IllegalStateException("analytics export request is not pending approval");
        }
        return new AnalyticsExportRequest(
                tenantId,
                linkId,
                applicationId,
                window,
                requestedByUserId,
                requestedAt,
                Status.APPROVED
        );
    }

    public enum Status {
        PENDING_APPROVAL,
        APPROVED,
        EXECUTED,
        REJECTED
    }
}
