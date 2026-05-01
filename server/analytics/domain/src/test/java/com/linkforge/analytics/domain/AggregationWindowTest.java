package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationWindowTest {

    @Test
    void of_shouldAcceptForwardWindow() {
        AggregationWindow window = AggregationWindow.of(
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z")
        );

        assertThat(window.toExclusive()).isAfter(window.fromInclusive());
        assertThat(window.granularity()).isEqualTo(AggregationWindow.Granularity.DAY);
    }

    @Test
    void of_shouldRejectBackwardWindow() {
        assertThatThrownBy(() -> AggregationWindow.of(
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toExclusive");
    }
}
