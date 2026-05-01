package com.linkforge.analytics.domain;

import java.time.Duration;

public class AggregationPolicy {

    private static final Duration MAX_DAILY_WINDOW = Duration.ofDays(366);

    public void requireAllowed(AggregationWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("window must be provided");
        }
        if (window.granularity() == AggregationWindow.Granularity.DAY
                && window.duration().compareTo(MAX_DAILY_WINDOW) > 0) {
            throw new IllegalArgumentException("window is too wide for daily aggregation");
        }
    }

    public int normalizeLimit(int requested, int defaultLimit, int maxLimit) {
        int safeDefault = defaultLimit <= 0 ? 20 : defaultLimit;
        int safeMax = maxLimit <= 0 ? safeDefault : maxLimit;
        if (requested <= 0) {
            return Math.min(safeDefault, safeMax);
        }
        return Math.min(requested, safeMax);
    }
}
