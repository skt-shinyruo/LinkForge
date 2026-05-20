package com.linkforge.foundation.eventing;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventStore {
    long loadMaxSeq();

    List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit);

    List<IntegrationEventRow> listAfterSeqByProducer(String producer, long lastSeqExclusive, int limit);

    void append(
            String eventId,
            String producer,
            String eventType,
            Long tenantId,
            String aggregateType,
            Long aggregateId,
            Instant occurredAtUtc,
            String payloadJson
    );
}
