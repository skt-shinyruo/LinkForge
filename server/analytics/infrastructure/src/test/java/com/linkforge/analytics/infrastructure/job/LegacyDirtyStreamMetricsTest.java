package com.linkforge.analytics.infrastructure.job;

import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyDirtyStreamMetricsTest {

    @Test
    void publish_shouldAggregateRetainedHistoryAndRemainingWorkAcrossBackfillStreams() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> recent = mock(MapRecord.class);
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> older = mock(MapRecord.class);
        PendingMessagesSummary dayOnePending = mock(PendingMessagesSummary.class);
        PendingMessagesSummary dayTwoPending = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.size("day-1")).thenReturn(10L);
        when(streams.size("day-2")).thenReturn(4L);
        when(streams.reverseRange(eq("day-1"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(recent));
        when(streams.reverseRange(eq("day-2"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(older));
        when(recent.getId()).thenReturn(RecordId.of(System.currentTimeMillis() - 1_000L, 0L));
        when(older.getId()).thenReturn(RecordId.of(System.currentTimeMillis() - 5_000L, 0L));
        when(streams.pending("day-1", "group")).thenReturn(dayOnePending);
        when(streams.pending("day-2", "group")).thenReturn(dayTwoPending);
        when(dayOnePending.getTotalPendingMessages()).thenReturn(0L);
        when(dayTwoPending.getTotalPendingMessages()).thenReturn(2L);
        when(redis.execute(any(RedisCallback.class))).thenReturn(groupState(0L), groupState(3L));
        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_stats_flush", metrics, false);

        LegacyDirtyStreamMetrics.Aggregate aggregate =
                new LegacyDirtyStreamMetrics(redis, consumer, metrics).start("link");
        aggregate.observe("day-1");
        aggregate.observe("day-2");
        RedisStreamBatchConsumer.StreamState state = aggregate.publish();
        consumer.publishStreamState(state);

        verify(metrics).set("linkforge.analytics.dirty.legacy.retained_entries", 14L,
                "marker", "link");
        verify(metrics).set("linkforge.analytics.dirty.legacy.lag", 3L,
                "marker", "link");
        verify(metrics).set("linkforge.analytics.dirty.legacy.pending", 2L,
                "marker", "link");
        verify(metrics).set("linkforge.analytics.dirty.legacy.remaining", 5L,
                "marker", "link");
        verify(metrics).set("linkforge.analytics.dirty.legacy.observation_degraded", 0L,
                "marker", "link");

        verify(metrics).set("linkforge.stream.remaining", 5L,
                "operation", "analytics_stats_flush");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_stats_flush", "state", "draining");
        assertThat(state).isEqualTo(
                RedisStreamBatchConsumer.StreamState.observed(3L, 2L, 0L, 0L));
    }

    @Test
    void publish_shouldExposeUnknownRemainingAndDegradedWhenGroupObservationFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary pending = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.size("day-1")).thenReturn(2L);
        when(streams.reverseRange(eq("day-1"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of());
        when(streams.pending("day-1", "group")).thenReturn(pending);
        when(pending.getTotalPendingMessages()).thenReturn(0L);
        when(redis.execute(any(RedisCallback.class))).thenReturn(null);
        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_dimension_flush", metrics, false);

        LegacyDirtyStreamMetrics.Aggregate aggregate =
                new LegacyDirtyStreamMetrics(redis, consumer, metrics).start("dimension");
        aggregate.observe("day-1");
        RedisStreamBatchConsumer.StreamState state = aggregate.publish();
        consumer.publishStreamState(state);

        verify(metrics).set("linkforge.analytics.dirty.legacy.retained_entries", 2L,
                "marker", "dimension");
        verify(metrics).set("linkforge.analytics.dirty.legacy.remaining", -1L,
                "marker", "dimension");
        verify(metrics).set("linkforge.analytics.dirty.legacy.observation_degraded", 1L,
                "marker", "dimension");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_dimension_flush", "state", "degraded");
        assertThat(state.observed()).isFalse();
    }

    private static List<List<Object>> groupState(long lag) {
        return List.of(List.of(
                "name".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "group".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "lag".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                lag,
                "last-delivered-id".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "1-0".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ));
    }
}
