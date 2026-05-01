package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregationPolicyTest {

    private final AggregationPolicy policy = new AggregationPolicy();

    @Test
    void normalizeLimit_shouldClampToDefaultAndMaximum() {
        assertThat(policy.normalizeLimit(0, 20, 100)).isEqualTo(20);
        assertThat(policy.normalizeLimit(150, 20, 100)).isEqualTo(100);
        assertThat(policy.normalizeLimit(50, 20, 100)).isEqualTo(50);
    }

    @Test
    void requireAllowedWindow_shouldRejectTooWideDailyWindow() {
        AggregationWindow window = AggregationWindow.of(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z")
        );

        assertThatThrownBy(() -> policy.requireAllowed(window))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }
}
