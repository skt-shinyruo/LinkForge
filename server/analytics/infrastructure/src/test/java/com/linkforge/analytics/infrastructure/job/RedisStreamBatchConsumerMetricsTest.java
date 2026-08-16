package com.linkforge.analytics.infrastructure.job;

import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamBatchConsumerMetricsTest {

    @Test
    void readNext_shouldTreatAcknowledgedRetainedHistoryAsNoTraffic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(0L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(streams.size("stats:visit:events")).thenReturn(10L);
        when(redis.execute(any(RedisCallback.class))).thenReturn(List.of(List.of(
                bytes("name"), bytes("group"),
                bytes("lag"), 0L,
                bytes("last-delivered-id"), bytes("1710000000000-0")
        )));
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());

        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "no_traffic");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "draining");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "backlog");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "degraded");
        verify(metrics).set("linkforge.stream.remaining", 0L,
                "operation", "analytics_visit_ingest");
    }

    @Test
    void readNext_shouldTreatUndeliveredRecordsWithoutPendingAsDraining() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(0L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(redis.execute(any(RedisCallback.class))).thenReturn(List.of(List.of(
                bytes("name"), bytes("group"),
                bytes("lag"), 4L,
                bytes("last-delivered-id"), bytes("1710000000000-0")
        )));
        when(streams.range(eq("stats:visit:events"), any(Range.class), any())).thenReturn(List.of());
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());

        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "no_traffic");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "draining");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "backlog");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "degraded");
    }

    @Test
    void readNext_shouldClassifyConsecutiveGrowthAsBacklog() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        PendingMessages pending = mock(PendingMessages.class);
        PendingMessage message = mock(PendingMessage.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(2L, 3L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(streams.pending(eq("stats:visit:events"), eq("group"), any(Range.class), anyLong()))
                .thenReturn(pending);
        when(pending.iterator()).thenReturn(List.of(message).iterator());
        when(message.getElapsedTimeSinceLastDelivery()).thenReturn(Duration.ofSeconds(5));
        when(streams.size("stats:visit:events")).thenReturn(10L);
        when(redis.execute(any(RedisCallback.class))).thenReturn(
                List.of(List.of(
                        bytes("name"), bytes("group"),
                        bytes("lag"), 4L,
                        bytes("last-delivered-id"), bytes("1710000000000-0")
                )),
                List.of(List.of(
                        bytes("name"), bytes("group"),
                        bytes("lag"), 5L,
                        bytes("last-delivered-id"), bytes("1710000000000-0")
                ))
        );
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());

        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);
        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics).set("linkforge.stream.oldest_unprocessed_age_millis", 5_000L,
                "operation", "analytics_visit_ingest");
        verify(metrics, times(2)).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "no_traffic");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "draining");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "backlog");
    }

    @Test
    void readNext_shouldKeepDecreasingWorkInDrainingState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(2L, 1L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(redis.execute(any(RedisCallback.class))).thenReturn(
                groupState(4L),
                groupState(2L)
        );
        when(streams.range(eq("stats:visit:events"), any(Range.class), any())).thenReturn(List.of());
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());

        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);
        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics, times(2)).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "draining");
        verify(metrics, times(2)).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "backlog");
    }

    @Test
    void readNext_shouldClassifyUnknownXinfoAsDegradedInsteadOfNoTraffic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(0L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(redis.execute(any(RedisCallback.class))).thenReturn(null);
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());

        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics).set("linkforge.stream.observation_available", 0L,
                "operation", "analytics_visit_ingest");
        verify(metrics).set("linkforge.job.health", 0L,
                "job", "analytics_visit_ingest", "state", "no_traffic");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "degraded");
    }

    @Test
    void readNext_shouldRestartTrendAfterObservationRecovery() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(summary.getTotalPendingMessages()).thenReturn(0L);
        when(streams.pending("stats:visit:events", "group")).thenReturn(summary);
        when(redis.execute(any(RedisCallback.class))).thenReturn(
                groupState(1L),
                null,
                groupState(5L)
        );
        when(streams.range(eq("stats:visit:events"), any(Range.class), any())).thenReturn(List.of());
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.<MapRecord<String, Object, Object>>of());
        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);

        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);
        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);
        consumer.readNext("stats:visit:events", 200, null, false, Duration.ZERO, 1);

        verify(metrics, times(2)).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "draining");
        verify(metrics).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "degraded");
        verify(metrics, never()).set("linkforge.job.health", 1L,
                "job", "analytics_visit_ingest", "state", "backlog");
    }

    @Test
    void acknowledge_shouldReturnOnlyRedisConfirmedCountAndReturnZeroOnFailure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.acknowledge(eq("stats:visit:events"), eq("group"), any(RecordId[].class)))
                .thenReturn(1L)
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisStreamBatchConsumer consumer = new RedisStreamBatchConsumer(
                redis, "group", "consumer", "analytics_visit_ingest", metrics);
        List<RecordId> ids = List.of(RecordId.of("1-0"), RecordId.of("2-0"));

        long partial = consumer.acknowledge("stats:visit:events", ids);
        long failed = consumer.acknowledge("stats:visit:events", ids);

        assertThat(partial).isEqualTo(1L);
        assertThat(failed).isZero();
        verify(metrics).add("linkforge.stream.acknowledged", 1L,
                "operation", "analytics_visit_ingest");
        verify(metrics).increment("linkforge.stream.failures",
                "operation", "analytics_visit_ingest", "stage", "ack");
        verify(metrics).increment("linkforge.stream.failures",
                "operation", "analytics_visit_ingest", "stage", "partial_ack");
    }

    @Test
    void streamState_shouldCombineLinkAndScopeWithoutIntroducingUnboundedLabels() {
        RedisStreamBatchConsumer.StreamState combined = RedisStreamBatchConsumer.StreamState.combine(
                RedisStreamBatchConsumer.StreamState.observed(3L, 2L, 1_000L, 5_000L),
                RedisStreamBatchConsumer.StreamState.observed(4L, 1L, 2_000L, 4_000L)
        );

        assertThat(combined).isEqualTo(
                RedisStreamBatchConsumer.StreamState.observed(7L, 3L, 2_000L, 5_000L));
        assertThat(RedisStreamBatchConsumer.StreamState.combine(
                combined,
                RedisStreamBatchConsumer.StreamState.unknown()
        ).observed()).isFalse();
    }

    private static List<List<Object>> groupState(long lag) {
        return List.of(List.of(
                bytes("name"), bytes("group"),
                bytes("lag"), lag,
                bytes("last-delivered-id"), bytes("1710000000000-0")
        ));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
