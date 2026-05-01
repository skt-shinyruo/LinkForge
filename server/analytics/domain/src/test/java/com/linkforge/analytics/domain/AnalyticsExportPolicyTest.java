package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsExportPolicyTest {

    private final AnalyticsExportPolicy policy = new AnalyticsExportPolicy();

    @Test
    void resolveWindow_shouldDefaultToPreviousDayWhenNoRangeProvided() {
        Instant now = Instant.parse("2026-04-30T12:00:00Z");

        AggregationWindow window = policy.resolveWindow(null, null, now);

        assertThat(window.fromInclusive()).isEqualTo(now.minus(1, ChronoUnit.DAYS));
        assertThat(window.toExclusive()).isEqualTo(now);
    }

    @Test
    void resolveWindow_shouldRejectBackwardWindow() {
        assertThatThrownBy(() -> policy.resolveWindow(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-30T12:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toExclusive");
    }
}
