package com.linkforge.analytics.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkDeletedV1;
import com.linkforge.foundation.eventing.IntegrationCheckpointRepository;
import com.linkforge.foundation.eventing.IntegrationDeadLetterRepository;
import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkCatalogProjectorJobTest {

    @Test
    void deletedEvent_shouldKeepScopeAndDisplaySnapshotWhileMarkingDeleted() throws Exception {
        SpyIntegrationEventStore store = new SpyIntegrationEventStore();
        FakeCheckpointRepository checkpoints = new FakeCheckpointRepository(10L);
        RecordingDeadLetterRepository deadLetter = new RecordingDeadLetterRepository();
        RecordingCatalogMapper catalogMapper = new RecordingCatalogMapper();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ShortLinkCatalogProjectorJob job = new ShortLinkCatalogProjectorJob(
                store,
                checkpoints,
                deadLetter,
                catalogMapper,
                objectMapper,
                noOpTxManager()
        );

        ShortLinkPublicSnapshot snapshot = new ShortLinkPublicSnapshot(
                1L,
                101L,
                "gone101",
                "go.example.test",
                "https://example.com/deleted",
                false,
                null,
                302,
                false,
                null,
                "ALL",
                List.of(),
                Instant.parse("2026-04-24T00:00:00Z"),
                2001L,
                3001L
        );
        String payload = objectMapper.writeValueAsString(new ShortLinkDeletedV1(
                "evt-1",
                Instant.parse("2026-04-24T10:00:00Z"),
                1L,
                101L,
                "gone101",
                snapshot
        ));
        IntegrationEventRow event = new IntegrationEventRow(
                11L,
                "evt-1",
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_DELETED_V1,
                1L,
                "shortlink",
                101L,
                Instant.parse("2026-04-24T10:00:00Z"),
                payload
        );

        store.rowsToReturn = List.of(event);

        int processed = job.projectOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(catalogMapper.rows).hasSize(1);
        AnalyticsLinkCatalogRow row = catalogMapper.rows.get(0);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getLinkId()).isEqualTo(101L);
        assertThat(row.getApplicationId()).isEqualTo(2001L);
        assertThat(row.getDomainId()).isEqualTo(3001L);
        assertThat(row.getCode()).isEqualTo("gone101");
        assertThat(row.getOriginalUrl()).isEqualTo("https://example.com/deleted");
        assertThat(row.getDeleted()).isTrue();
        assertThat(checkpoints.updatedConsumer).isEqualTo(ShortLinkCatalogProjectorJob.CONSUMER);
        assertThat(checkpoints.updatedLastSeq).isEqualTo(11L);
        assertThat(store.listAfterSeqCalls).isZero();
    }

    @Test
    void projectOnce_shouldQueryShortLinkProducerStreamOnly() {
        SpyIntegrationEventStore store = new SpyIntegrationEventStore();
        FakeCheckpointRepository checkpoints = new FakeCheckpointRepository(10L);
        RecordingDeadLetterRepository deadLetter = new RecordingDeadLetterRepository();
        RecordingCatalogMapper catalogMapper = new RecordingCatalogMapper();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ShortLinkCatalogProjectorJob job = new ShortLinkCatalogProjectorJob(
                store,
                checkpoints,
                deadLetter,
                catalogMapper,
                objectMapper,
                noOpTxManager()
        );

        int processed = job.projectOnce();

        assertThat(processed).isZero();
        assertThat(store.listAfterSeqByProducerCalls).isEqualTo(1);
        assertThat(store.producer).isEqualTo("shortlink");
        assertThat(store.lastSeqExclusive).isEqualTo(10L);
        assertThat(store.limit).isEqualTo(ShortLinkCatalogProjectorJob.BATCH_LIMIT);
        assertThat(store.listAfterSeqCalls).isZero();
    }

    private static PlatformTransactionManager noOpTxManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static class SpyIntegrationEventStore implements IntegrationEventStore {
        private List<IntegrationEventRow> rowsToReturn = List.of();
        private int listAfterSeqCalls;
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
            this.listAfterSeqCalls++;
            return List.of();
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
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeCheckpointRepository implements IntegrationCheckpointRepository {
        private final long lastSeq;
        private String updatedConsumer;
        private long updatedLastSeq;

        private FakeCheckpointRepository(long lastSeq) {
            this.lastSeq = lastSeq;
        }

        @Override
        public long loadOrInit(String consumer) {
            return lastSeq;
        }

        @Override
        public void update(String consumer, long lastSeq) {
            this.updatedConsumer = consumer;
            this.updatedLastSeq = lastSeq;
        }
    }

    private static class RecordingDeadLetterRepository implements IntegrationDeadLetterRepository {
        @Override
        public void upsertFailure(String consumer, IntegrationEventRow event, int attempts, String lastError) {
        }
    }

    private static class RecordingCatalogMapper implements AnalyticsLinkCatalogMapper {
        private final List<AnalyticsLinkCatalogRow> rows = new java.util.ArrayList<>();

        @Override
        public int upsert(AnalyticsLinkCatalogRow row) {
            rows.add(row);
            return 1;
        }
    }
}
