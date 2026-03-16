package com.linkforge.foundation.eventing;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationEventRowTest {

    @Test
    void should_expose_record_components() {
        Instant now = Instant.now();
        IntegrationEventRow row = new IntegrationEventRow(
                42L,
                "evt-1",
                "shortlink",
                "shortlink.ShortLinkCreated.v1",
                1L,
                "shortlink",
                100L,
                now,
                "{\"k\":\"v\"}"
        );

        assertThat(row.seq()).isEqualTo(42L);
        assertThat(row.eventId()).isEqualTo("evt-1");
        assertThat(row.producer()).isEqualTo("shortlink");
        assertThat(row.eventType()).isEqualTo("shortlink.ShortLinkCreated.v1");
        assertThat(row.tenantId()).isEqualTo(1L);
        assertThat(row.aggregateType()).isEqualTo("shortlink");
        assertThat(row.aggregateId()).isEqualTo(100L);
        assertThat(row.occurredAtUtc()).isEqualTo(now);
        assertThat(row.payloadJson()).isEqualTo("{\"k\":\"v\"}");
    }
}

