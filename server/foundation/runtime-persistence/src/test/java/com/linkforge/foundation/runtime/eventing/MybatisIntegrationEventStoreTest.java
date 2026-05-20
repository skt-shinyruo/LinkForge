package com.linkforge.foundation.runtime.eventing;

import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.runtime.persistence.mapper.IntegrationEventMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisIntegrationEventStoreTest {

    @Test
    void listAfterSeqByProducer_shouldDelegateToMapperWithProducerFilter() {
        SpyIntegrationEventMapper mapper = new SpyIntegrationEventMapper();
        MybatisIntegrationEventStore store = new MybatisIntegrationEventStore(mapper);
        IntegrationEventRow row = new IntegrationEventRow(
                12L,
                "evt-12",
                "shortlink",
                "shortlink.ShortLinkCreated.v1",
                1L,
                "shortlink",
                101L,
                Instant.parse("2026-05-19T00:00:00Z"),
                "{}"
        );

        mapper.rowsToReturn = List.of(row);

        List<IntegrationEventRow> rows = store.listAfterSeqByProducer("shortlink", 10L, 50);

        assertThat(rows).containsExactly(row);
        assertThat(mapper.listAfterSeqByProducerCalls).isEqualTo(1);
        assertThat(mapper.producer).isEqualTo("shortlink");
        assertThat(mapper.lastSeqExclusive).isEqualTo(10L);
        assertThat(mapper.limit).isEqualTo(50);
    }

    private static class SpyIntegrationEventMapper implements IntegrationEventMapper {
        private List<IntegrationEventRow> rowsToReturn = List.of();
        private int listAfterSeqByProducerCalls;
        private String producer;
        private long lastSeqExclusive;
        private int limit;

        @Override
        public long loadMaxSeq() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IntegrationEventRow> listAfterSeq(long lastSeqExclusive, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IntegrationEventRow> listAfterSeqByProducer(String producer, long lastSeqExclusive, int limit) {
            this.listAfterSeqByProducerCalls++;
            this.producer = producer;
            this.lastSeqExclusive = lastSeqExclusive;
            this.limit = limit;
            return rowsToReturn;
        }

        @Override
        public int insert(
                String eventId,
                String producer,
                String eventType,
                Long tenantId,
                String aggregateType,
                Long aggregateId,
                Instant occurredAtUtc,
                String payloadJson
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
