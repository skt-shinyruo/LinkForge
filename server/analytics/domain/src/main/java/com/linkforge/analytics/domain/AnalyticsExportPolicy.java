package com.linkforge.analytics.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class AnalyticsExportPolicy {

    public AggregationWindow resolveWindow(Instant requestedFromInclusive, Instant requestedToExclusive, Instant now) {
        Objects.requireNonNull(now, "now must be provided");
        Instant effectiveTo = requestedToExclusive == null ? now : requestedToExclusive;
        Instant effectiveFrom = requestedFromInclusive == null
                ? effectiveTo.minus(1, ChronoUnit.DAYS)
                : requestedFromInclusive;
        return AggregationWindow.of(effectiveFrom, effectiveTo);
    }

    public boolean requiresApproval(AggregationWindow window) {
        if (window == null) {
            return false;
        }
        return window.duration().compareTo(java.time.Duration.ofHours(1)) > 0;
    }
}
