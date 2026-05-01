package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitFactTest {

    @Test
    void create_shouldOwnNormalizedVisitFact() {
        VisitDimension dimension = new VisitDimension(
                "1.2.3.4",
                "Mozilla/5.0",
                "example.com",
                "zh-cn",
                Map.of("utm_source", "newsletter")
        );

        VisitFact fact = VisitFact.create(
                1L,
                10L,
                Instant.parse("2026-04-30T12:00:00Z"),
                20L,
                30L,
                "abc123",
                "https://example.com/live",
                dimension
        );

        assertThat(fact.tenantId()).isEqualTo(1L);
        assertThat(fact.dimension()).isSameAs(dimension);
    }

    @Test
    void create_shouldRejectInvalidIdentity() {
        assertThatThrownBy(() -> VisitFact.create(
                0L,
                10L,
                Instant.parse("2026-04-30T12:00:00Z"),
                null,
                null,
                "abc123",
                "https://example.com/live",
                VisitDimension.empty()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
