package com.linkforge.analytics.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.contract.shortlink.event.ShortLinkDeletedV1;
import com.linkforge.foundation.eventing.IntegrationCheckpointRepository;
import com.linkforge.foundation.eventing.IntegrationDeadLetterRepository;
import com.linkforge.foundation.eventing.IntegrationEventRow;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ShortLinkCatalogProjectorJobTest {

    @Test
    void projectOnce_should_upsert_catalog_rows_and_advance_checkpoint() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        InMemoryCheckpointRepo checkpoints = new InMemoryCheckpointRepo();
        IntegrationDeadLetterRepository deadLetter = mock(IntegrationDeadLetterRepository.class);
        AnalyticsLinkCatalogMapper catalogMapper = mock(AnalyticsLinkCatalogMapper.class);
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op
            }
        };

        long tenantId = 1L;
        long linkId = 10L;
        String code = "Abc123";
        String url = "https://example.com/a";
        Instant now = Instant.parse("2026-03-16T00:00:00Z");

        ShortLinkPublicSnapshot createdSnap = new ShortLinkPublicSnapshot(
                tenantId, linkId, code, null, url, true, null, null, false,
                null, null, List.of(), null, 101L, 201L
        );
        ShortLinkCreatedV1 created = new ShortLinkCreatedV1("e1", now, tenantId, linkId, code, createdSnap);

        Instant archivedAt = Instant.parse("2026-03-16T01:00:00Z");
        ShortLinkPublicSnapshot archivedSnap = new ShortLinkPublicSnapshot(
                tenantId, linkId, code, null, url, true, null, null, false,
                null, null, List.of(), archivedAt, 101L, 201L
        );
        ShortLinkArchivedV1 archived = new ShortLinkArchivedV1("e2", archivedAt, tenantId, linkId, code, archivedSnap);

        ShortLinkPublicSnapshot deletedSnap = new ShortLinkPublicSnapshot(
                tenantId, linkId, code, null, url, true, null, null, false,
                null, null, List.of(), null, 101L, 201L
        );
        ShortLinkDeletedV1 deleted = new ShortLinkDeletedV1("e3", Instant.parse("2026-03-16T02:00:00Z"), tenantId, linkId, code, deletedSnap);

        List<IntegrationEventRow> rows = List.of(
                new IntegrationEventRow(
                        1L,
                        "e1",
                        "shortlink",
                        ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                        tenantId,
                        "shortlink",
                        linkId,
                        now,
                        objectMapper.writeValueAsString(created)
                ),
                new IntegrationEventRow(
                        2L,
                        "e2",
                        "shortlink",
                        ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1,
                        tenantId,
                        "shortlink",
                        linkId,
                        archivedAt,
                        objectMapper.writeValueAsString(archived)
                ),
                new IntegrationEventRow(
                        3L,
                        "e3",
                        "shortlink",
                        ShortLinkEventTypes.SHORT_LINK_DELETED_V1,
                        tenantId,
                        "shortlink",
                        linkId,
                        Instant.parse("2026-03-16T02:00:00Z"),
                        objectMapper.writeValueAsString(deleted)
                )
        );

        when(store.listAfterSeq(0L, 200)).thenReturn(rows);
        when(store.listAfterSeq(3L, 200)).thenReturn(List.of());

        ShortLinkCatalogProjectorJob job = new ShortLinkCatalogProjectorJob(
                store,
                checkpoints,
                deadLetter,
                catalogMapper,
                objectMapper,
                txManager
        );

        int processed = job.projectOnce();

        assertThat(processed).isEqualTo(3);
        assertThat(checkpoints.lastSeq).isEqualTo(3L);

        ArgumentCaptor<AnalyticsLinkCatalogRow> rowCaptor = ArgumentCaptor.forClass(AnalyticsLinkCatalogRow.class);
        verify(catalogMapper, times(3)).upsert(rowCaptor.capture());

        List<AnalyticsLinkCatalogRow> upserts = rowCaptor.getAllValues();
        assertThat(upserts).hasSize(3);

        AnalyticsLinkCatalogRow createdRow = upserts.get(0);
        assertThat(createdRow.getTenantId()).isEqualTo(tenantId);
        assertThat(createdRow.getLinkId()).isEqualTo(linkId);
        assertThat(createdRow.getCode()).isEqualTo(code);
        assertThat(createdRow.getOriginalUrl()).isEqualTo(url);
        assertThat(createdRow.getDeleted()).isEqualTo(false);

        AnalyticsLinkCatalogRow archivedRow = upserts.get(1);
        assertThat(archivedRow.getArchivedAt()).isNotNull();
        assertThat(archivedRow.getDeleted()).isEqualTo(false);

        AnalyticsLinkCatalogRow deletedRow = upserts.get(2);
        assertThat(deletedRow.getDeleted()).isEqualTo(true);
        assertThat(deletedRow.getCode()).isNull();
        assertThat(deletedRow.getOriginalUrl()).isNull();
    }

    @Test
    void projectOnce_should_deadletter_poison_event_and_still_advance_checkpoint() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        InMemoryCheckpointRepo checkpoints = new InMemoryCheckpointRepo();
        IntegrationDeadLetterRepository deadLetter = mock(IntegrationDeadLetterRepository.class);
        AnalyticsLinkCatalogMapper catalogMapper = mock(AnalyticsLinkCatalogMapper.class);
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op
            }
        };

        IntegrationEventRow poison = new IntegrationEventRow(
                10L,
                "e10",
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                1L,
                "shortlink",
                10L,
                Instant.parse("2026-03-16T00:00:00Z"),
                "{invalid-json"
        );

        when(store.listAfterSeq(0L, 200)).thenReturn(List.of(poison));
        when(store.listAfterSeq(10L, 200)).thenReturn(List.of());

        ShortLinkCatalogProjectorJob job = new ShortLinkCatalogProjectorJob(
                store,
                checkpoints,
                deadLetter,
                catalogMapper,
                objectMapper,
                txManager
        );

        int processed = job.projectOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(checkpoints.lastSeq).isEqualTo(10L);
        verify(deadLetter).upsertFailure(anyString(), any(IntegrationEventRow.class), anyInt(), anyString());
    }

    @Test
    void projectOnce_should_not_advance_checkpoint_on_transient_db_error() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        InMemoryCheckpointRepo checkpoints = new InMemoryCheckpointRepo();
        IntegrationDeadLetterRepository deadLetter = mock(IntegrationDeadLetterRepository.class);
        AnalyticsLinkCatalogMapper catalogMapper = mock(AnalyticsLinkCatalogMapper.class);
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // no-op
            }

            @Override
            public void rollback(TransactionStatus status) {
                // no-op
            }
        };

        long tenantId = 1L;
        long linkId = 10L;
        String code = "Abc123";
        String url = "https://example.com/a";
        Instant now = Instant.parse("2026-03-16T00:00:00Z");

        ShortLinkPublicSnapshot createdSnap = new ShortLinkPublicSnapshot(
                tenantId, linkId, code, null, url, true, null, null, false,
                null, null, List.of(), null, 101L, 201L
        );
        ShortLinkCreatedV1 created = new ShortLinkCreatedV1("e1", now, tenantId, linkId, code, createdSnap);

        IntegrationEventRow row = new IntegrationEventRow(
                10L,
                "e1",
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                tenantId,
                "shortlink",
                linkId,
                now,
                objectMapper.writeValueAsString(created)
        );

        when(store.listAfterSeq(0L, 200)).thenReturn(List.of(row));
        when(catalogMapper.upsert(any())).thenThrow(new TransientDataAccessResourceException("db down"));

        ShortLinkCatalogProjectorJob job = new ShortLinkCatalogProjectorJob(
                store,
                checkpoints,
                deadLetter,
                catalogMapper,
                objectMapper,
                txManager
        );

        int processed = job.projectOnce();

        assertThat(processed).isEqualTo(0);
        assertThat(checkpoints.lastSeq).isEqualTo(0L);
        verify(deadLetter, never()).upsertFailure(anyString(), any(IntegrationEventRow.class), anyInt(), anyString());
    }

    private static final class InMemoryCheckpointRepo implements IntegrationCheckpointRepository {
        private long lastSeq;

        @Override
        public long loadOrInit(String consumer) {
            return lastSeq;
        }

        @Override
        public void update(String consumer, long lastSeq) {
            this.lastSeq = lastSeq;
        }
    }
}
