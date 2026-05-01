package com.linkforge.analytics.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AggregationWindow(
        Instant fromInclusive,
        Instant toExclusive,
        Granularity granularity
) {

    public AggregationWindow {
        Objects.requireNonNull(fromInclusive, "fromInclusive must be provided");
        Objects.requireNonNull(toExclusive, "toExclusive must be provided");
        granularity = granularity == null ? Granularity.DAY : granularity;
        if (!toExclusive.isAfter(fromInclusive)) {
            throw new IllegalArgumentException("toExclusive must be after fromInclusive");
        }
    }

    public static AggregationWindow of(Instant fromInclusive, Instant toExclusive) {
        return new AggregationWindow(fromInclusive, toExclusive, Granularity.DAY);
    }

    public static AggregationWindow of(Instant fromInclusive, Instant toExclusive, Granularity granularity) {
        return new AggregationWindow(fromInclusive, toExclusive, granularity);
    }

    public Duration duration() {
        return Duration.between(fromInclusive, toExclusive);
    }

    public enum Granularity {
        HOUR,
        DAY
    }
}
