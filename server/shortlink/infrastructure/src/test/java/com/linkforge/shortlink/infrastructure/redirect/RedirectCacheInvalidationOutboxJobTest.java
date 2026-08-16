package com.linkforge.shortlink.infrastructure.redirect;

import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectCacheInvalidationOutboxJobTest {

    @Test
    void processOnce_shouldEvictDueInvalidationAndMarkProcessed() {
        RedirectCacheInvalidationOutboxRepository outbox = mock(RedirectCacheInvalidationOutboxRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T01:02:03Z"), ZoneOffset.UTC);
        RedirectCacheInvalidationOutboxJob job = new RedirectCacheInvalidationOutboxJob(outbox, redirectCacheSync, clock);
        LocalDateTime nowUtc = LocalDateTime.parse("2026-05-19T01:02:03");
        RedirectCacheInvalidationOutboxRow row = new RedirectCacheInvalidationOutboxRow(
                11L,
                1L,
                3001L,
                "abc123",
                "PENDING",
                7L,
                0
        );
        when(outbox.listDue(nowUtc, RedirectCacheInvalidationOutboxJob.BATCH_LIMIT)).thenReturn(List.of(row));

        int processed = job.processOnce();

        assertThat(processed).isEqualTo(1);
        verify(redirectCacheSync).evict(1L, 3001L, "abc123");
        verify(outbox).markProcessed(11L, 7L, nowUtc);
        verify(outbox, never()).markFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void processOnce_shouldRecordFailureAndLeaveInvalidationPendingForRetry() {
        RedirectCacheInvalidationOutboxRepository outbox = mock(RedirectCacheInvalidationOutboxRepository.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T01:02:03Z"), ZoneOffset.UTC);
        RedirectCacheInvalidationOutboxJob job = new RedirectCacheInvalidationOutboxJob(outbox, redirectCacheSync, clock);
        LocalDateTime nowUtc = LocalDateTime.parse("2026-05-19T01:02:03");
        RedirectCacheInvalidationOutboxRow row = new RedirectCacheInvalidationOutboxRow(
                12L,
                1L,
                null,
                "legacy",
                "PENDING",
                3L,
                2
        );
        when(outbox.listDue(nowUtc, RedirectCacheInvalidationOutboxJob.BATCH_LIMIT)).thenReturn(List.of(row));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redirectCacheSync).evict(1L, null, "legacy");

        int processed = job.processOnce();

        assertThat(processed).isEqualTo(1);
        verify(redirectCacheSync).evict(1L, null, "legacy");
        verify(outbox, never()).markProcessed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
        ArgumentCaptor<LocalDateTime> nextAttemptAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outbox).markFailed(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(3),
                org.mockito.ArgumentMatchers.eq("redis unavailable"),
                nextAttemptAt.capture()
        );
        assertThat(nextAttemptAt.getValue()).isAfter(nowUtc);
    }
}
