package com.linkforge.redirect.infrastructure.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkEventProjectorJobTest {

    @Test
    void drain_should_ignore_unknown_shortlink_event_type_and_not_deadletter() {
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        InMemoryCheckpointRepo checkpoints = new InMemoryCheckpointRepo();
        IntegrationDeadLetterRepository deadLetters = mock(IntegrationDeadLetterRepository.class);
        RedirectLinkProjectionMapper projectionMapper = mock(RedirectLinkProjectionMapper.class);
        LinkCachePort linkCache = mock(LinkCachePort.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PlatformTransactionManager txManager = noOpTxManager();

        IntegrationEventRow unknown = new IntegrationEventRow(
                1L,
                "e1",
                "shortlink",
                "shortlink.future_event.v99",
                1L,
                "shortlink",
                1L,
                Instant.parse("2026-03-16T00:00:00Z"),
                "{\"any\":\"json\"}"
        );

        when(store.listAfterSeq(0L, 200)).thenReturn(List.of(unknown));

        ShortLinkEventProjectorJob job = new ShortLinkEventProjectorJob(
                store,
                checkpoints,
                deadLetters,
                projectionMapper,
                linkCache,
                objectMapper,
                txManager,
                200
        );

        job.drain();

        assertThat(checkpoints.lastSeq).isEqualTo(1L);
        verify(deadLetters, never()).upsertFailure(anyString(), any(IntegrationEventRow.class), anyInt(), anyString());
        verify(projectionMapper, never()).upsert(any());
        verify(projectionMapper, never()).deleteByHostnameAndCode(anyString(), anyString());
        verify(linkCache, never()).tryPut(any());
        verify(linkCache, never()).tryEvict(anyString());
    }

    @Test
    void drain_should_not_advance_checkpoint_when_cache_put_fails() throws Exception {
        IntegrationEventStore store = mock(IntegrationEventStore.class);
        InMemoryCheckpointRepo checkpoints = new InMemoryCheckpointRepo();
        IntegrationDeadLetterRepository deadLetters = mock(IntegrationDeadLetterRepository.class);
        RedirectLinkProjectionMapper projectionMapper = mock(RedirectLinkProjectionMapper.class);
        LinkCachePort linkCache = mock(LinkCachePort.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        PlatformTransactionManager txManager = noOpTxManager();

        long tenantId = 1L;
        long linkId = 10L;
        String code = "Abc123";
        String url = "https://example.com/a";
        Instant occurredAt = Instant.parse("2026-03-16T00:00:00Z");

        ShortLinkPublicSnapshot snapshot = new ShortLinkPublicSnapshot(
                tenantId,
                linkId,
                code,
                "alpha.example.test",
                url,
                true,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                null,
                null,
                null
        );
        ShortLinkCreatedV1 created = new ShortLinkCreatedV1("e1", occurredAt, tenantId, linkId, code, snapshot);

        IntegrationEventRow row1 = new IntegrationEventRow(
                1L,
                "e1",
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                tenantId,
                "shortlink",
                linkId,
                occurredAt,
                objectMapper.writeValueAsString(created)
        );
        IntegrationEventRow row2 = new IntegrationEventRow(
                2L,
                "e2",
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                tenantId,
                "shortlink",
                linkId,
                occurredAt,
                objectMapper.writeValueAsString(created)
        );

        when(store.listAfterSeq(0L, 200)).thenReturn(List.of(row1, row2));
        when(projectionMapper.upsert(any())).thenReturn(1);
        when(linkCache.tryEvict(eq("alpha.example.test"), eq(code))).thenReturn(true);
        when(linkCache.tryPut(eq("alpha.example.test"), any())).thenReturn(false);

        ShortLinkEventProjectorJob job = new ShortLinkEventProjectorJob(
                store,
                checkpoints,
                deadLetters,
                projectionMapper,
                linkCache,
                objectMapper,
                txManager,
                200
        );

        job.drain();

        assertThat(checkpoints.lastSeq).isEqualTo(0L);
        verify(deadLetters, never()).upsertFailure(anyString(), any(IntegrationEventRow.class), anyInt(), anyString());
        verify(projectionMapper, times(1)).upsert(any());
        verify(store, times(1)).listAfterSeq(eq(0L), anyInt());
        verify(linkCache, times(1)).tryPut(eq("alpha.example.test"), any());
    }

    private static PlatformTransactionManager noOpTxManager() {
        return new PlatformTransactionManager() {
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
