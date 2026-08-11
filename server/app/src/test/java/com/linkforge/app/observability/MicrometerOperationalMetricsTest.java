package com.linkforge.app.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicrometerOperationalMetricsTest {

    @Test
    void shouldPublishCountersTimersAndStableGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerOperationalMetrics metrics = new MicrometerOperationalMetrics(registry);

        metrics.increment("linkforge.test.events", "result", "ok");
        metrics.add("linkforge.test.events", 2L, "result", "ok");
        metrics.record("linkforge.test.duration", Duration.ofMillis(25L), "result", "ok");
        metrics.set("linkforge.test.backlog", 7L, "queue", "primary");
        metrics.set("linkforge.test.backlog", 3L, "queue", "primary");

        assertThat(registry.get("linkforge.test.events").tag("result", "ok").counter().count()).isEqualTo(3.0d);
        assertThat(registry.get("linkforge.test.duration").tag("result", "ok").timer().count()).isEqualTo(1L);
        assertThat(registry.get("linkforge.test.backlog").tag("queue", "primary").gauge().value()).isEqualTo(3.0d);
        assertThat(registry.find("linkforge.test.backlog").gauges()).hasSize(1);
    }

    @Test
    void shouldRejectOddTagListsAndIgnoreInvalidSamples() {
        MicrometerOperationalMetrics metrics = new MicrometerOperationalMetrics(new SimpleMeterRegistry());

        assertThatThrownBy(() -> metrics.increment("linkforge.test.events", "result"))
                .isInstanceOf(IllegalArgumentException.class);

        metrics.add("linkforge.test.events", 0L);
        metrics.record("linkforge.test.duration", Duration.ofMillis(-1L));
    }
}
