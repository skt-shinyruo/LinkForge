package com.linkforge.foundation.eventing;

import java.time.Instant;

public record IntegrationEventRow(
        long seq,
        String eventId,
        String producer,
        String eventType,
        Long tenantId,
        String aggregateType,
        Long aggregateId,
        Instant occurredAtUtc,
        String payloadJson
) {
}

