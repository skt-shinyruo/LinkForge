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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkCatalogProjectorJobTest {

    @Test
    void deletedEvent_shouldKeepScopeAndDisplaySnapshotWhileMarkingDeleted() throws Exception {
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        IntegrationCheckpointRepository checkpoints = mock(IntegrationCheckpointRepository.class);
        IntegrationDeadLetterRepository deadLetter = mock(IntegrationDeadLetterRepository.class);
        AnalyticsLinkCatalogMapper catalogMapper = mock(AnalyticsLinkCatalogMapper.class);
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

        when(checkpoints.loadOrInit(ShortLinkCatalogProjectorJob.CONSUMER)).thenReturn(10L);
        when(store.listAfterSeq(10L, ShortLinkCatalogProjectorJob.BATCH_LIMIT)).thenReturn(List.of(event));

        int processed = job.projectOnce();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<AnalyticsLinkCatalogRow> rowCaptor = ArgumentCaptor.forClass(AnalyticsLinkCatalogRow.class);
        verify(catalogMapper).upsert(rowCaptor.capture());
        AnalyticsLinkCatalogRow row = rowCaptor.getValue();
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getLinkId()).isEqualTo(101L);
        assertThat(row.getApplicationId()).isEqualTo(2001L);
        assertThat(row.getDomainId()).isEqualTo(3001L);
        assertThat(row.getCode()).isEqualTo("gone101");
        assertThat(row.getOriginalUrl()).isEqualTo("https://example.com/deleted");
        assertThat(row.getDeleted()).isTrue();
        verify(checkpoints).update(ShortLinkCatalogProjectorJob.CONSUMER, 11L);
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
}
