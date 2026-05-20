package com.linkforge.foundation.runtime.eventing;

import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import com.linkforge.foundation.runtime.persistence.mapper.IntegrationEventMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class MybatisIntegrationEventStore implements IntegrationEventStore {

    private final IntegrationEventMapper mapper;

    public MybatisIntegrationEventStore(IntegrationEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long loadMaxSeq() {
        return mapper.loadMaxSeq();
    }

    @Override
    public List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit) {
        return mapper.listAfterSeq(lastSeqExclusive, limit);
    }

    @Override
    public List<IntegrationEventRow> listAfterSeqByProducer(String producer, long lastSeqExclusive, int limit) {
        return mapper.listAfterSeqByProducer(producer, lastSeqExclusive, limit);
    }

    @Override
    public void append(
            String eventId,
            String producer,
            String eventType,
            Long tenantId,
            String aggregateType,
            Long aggregateId,
            Instant occurredAtUtc,
            String payloadJson
    ) {
        mapper.insert(eventId, producer, eventType, tenantId, aggregateType, aggregateId, occurredAtUtc, payloadJson);
    }
}
