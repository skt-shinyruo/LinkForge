package com.linkforge.analytics.infrastructure.job;

import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsRedirectEventProjectorJobTest {

    @Test
    void projectRecords_shouldStopCurrentDrainWhenAckIsPartial() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsRedisAggregateWriter aggregateWriter = mock(AnalyticsRedisAggregateWriter.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);
        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                new AnalyticsProperties(),
                aggregateWriter
        );
        Map<String, String> values = Map.of(
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        );

        boolean completed = job.projectRecords(
                AnalyticsKeys.visitEventStreamKey(),
                List.of(visitRecord("1-0", values), visitRecord("2-0", values))
        );

        assertThat(completed).isFalse();
        verify(aggregateWriter, times(2)).write(values);
    }

    @Test
    void aggregateWriter_should_route_replayed_requestId_through_the_same_atomic_dedup_script() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setRedisKeyTtlDays(7);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));

        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);

        AnalyticsRedisAggregateWriter writer = new AnalyticsRedisAggregateWriter(redis, properties);
        Map<String, String> event = Map.of(
                "requestId", "request-123",
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1",
                "refererDomain", "example.com"
        );

        writer.write(event);
        writer.write(event);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redis, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), any(Object[].class));
        assertThat(keysCaptor.getAllValues()).hasSize(2);
        assertThat(keysCaptor.getAllValues().get(0))
                .isEqualTo(keysCaptor.getAllValues().get(1))
                .contains(
                        AnalyticsKeys.projectionDedupKey("request-123"),
                        AnalyticsKeys.statsDirtyMarkerV2Key(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.scopeDirtyMarkerV2Key(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.scopeDirtyMarkerV2FirstSeenKey(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.dimDirtyMarkerV2Key(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.dimDirtyMarkerV2FirstSeenKey(LocalDate.of(2026, 4, 24))
                )
                .doesNotContain(
                        AnalyticsKeys.statsDirtyStreamKey(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.scopeDirtyStreamKey(LocalDate.of(2026, 4, 24)),
                        AnalyticsKeys.dimDirtyStreamKey(LocalDate.of(2026, 4, 24))
                );
        verify(redis, never()).opsForValue();
        verify(redis, never()).opsForHash();
        verify(redis, never()).opsForHyperLogLog();
        verify(redis, never()).opsForStream();
    }

    @Test
    void aggregateWriter_shouldKeepV2AndAddLegacyStreamsOnlyWhenRollbackFlagEnabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setRedisKeyTtlDays(7);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));
        properties.getDirtyMarker().setLegacyWriteEnabled(true);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        new AnalyticsRedisAggregateWriter(redis, properties).write(Map.of(
                "requestId", "rollback-request-1",
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "applicationId", "100",
                "domainId", "200",
                "visitorKey", "visitor-1",
                "refererDomain", "example.com"
        ));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redis).execute(any(RedisScript.class), keysCaptor.capture(), argsCaptor.capture());
        LocalDate day = LocalDate.of(2026, 4, 24);
        assertThat(keysCaptor.getValue()).contains(
                AnalyticsKeys.statsDirtyMarkerV2Key(day),
                AnalyticsKeys.scopeDirtyMarkerV2Key(day),
                AnalyticsKeys.dimDirtyMarkerV2Key(day),
                AnalyticsKeys.statsDirtyStreamKey(day),
                AnalyticsKeys.scopeDirtyStreamKey(day),
                AnalyticsKeys.dimDirtyStreamKey(day)
        );
        assertThat(argsCaptor.getValue()[5]).isEqualTo("1");
    }

    @Test
    void project_should_apply_async_aggregates_and_ack_after_successful_projection() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setRedisKeyTtlDays(7);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(AnalyticsKeys.visitEventStreamKey())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hllOps = mock(HyperLogLogOperations.class);
        when(redis.opsForHyperLogLog()).thenReturn(hllOps);
        when(hllOps.add(anyString(), any(String[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.increment(anyString(), any(), eq(1L))).thenReturn(1L);

        when(redis.expireAt(anyString(), any(Date.class))).thenReturn(true);
        MapRecord<String, Object, Object> record = visitRecord("1-0", Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "applicationId", "100",
                "domainId", "200",
                "visitorKey", "visitor-1",
                "refererDomain", "example.com"
        ));
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record), List.of());
        acknowledgeAll(streamOps);

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                new AnalyticsRedisAggregateWriter(redis, properties)
        );

        job.project();

        LocalDate day = LocalDate.of(2026, 4, 24);
        InOrder inOrder = inOrder(valueOps, hllOps, hashOps, streamOps);
        inOrder.verify(valueOps).increment(AnalyticsKeys.pvKey(1L, 10L, day));
        inOrder.verify(hllOps).add(eq(AnalyticsKeys.uvKey(1L, 10L, day)), eq(new String[]{"visitor-1"}));
        inOrder.verify(hllOps).add(eq(AnalyticsKeys.tenantScopeUvKey(1L, day)), eq(new String[]{"visitor-1"}));
        inOrder.verify(hllOps).add(eq(AnalyticsKeys.applicationScopeUvKey(1L, 100L, day)), eq(new String[]{"visitor-1"}));
        inOrder.verify(hllOps).add(eq(AnalyticsKeys.domainScopeUvKey(1L, 200L, day)), eq(new String[]{"visitor-1"}));
        inOrder.verify(hashOps).increment(AnalyticsKeys.dimPvHashKey(1L, 10L, day, "referer_domain"), "example.com", 1L);
        inOrder.verify(streamOps).acknowledge(eq(AnalyticsKeys.visitEventStreamKey()), eq("lf-visit-projector"), any(RecordId[].class));
        verify(redis, never()).opsForSet();

        verify(hashOps).increment(AnalyticsKeys.statsDirtyMarkerV2Key(day), "1:10", 1L);
        verify(hashOps).increment(AnalyticsKeys.scopeDirtyMarkerV2Key(day), "tenant:1:0", 1L);
        verify(hashOps).increment(AnalyticsKeys.scopeDirtyMarkerV2Key(day), "application:1:100", 1L);
        verify(hashOps).increment(AnalyticsKeys.scopeDirtyMarkerV2Key(day), "domain:1:200", 1L);
        verify(hashOps).increment(AnalyticsKeys.dimDirtyMarkerV2Key(day), "1:10", 1L);
        verify(streamOps, never()).add(any());
    }

    @Test
    void project_should_ack_legacy_records_without_projection_marker_and_skip_aggregate_writes() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(AnalyticsKeys.visitEventStreamKey())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));
        MapRecord<String, Object, Object> record = visitRecord("1-0", Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10"
        ));
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record), List.of());
        acknowledgeAll(streamOps);

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                new AnalyticsRedisAggregateWriter(redis, properties)
        );

        job.project();

        verify(streamOps).acknowledge(eq(AnalyticsKeys.visitEventStreamKey()), eq("lf-visit-projector"), any(RecordId[].class));
        verify(streamOps, never()).add(any());
    }

    @Test
    void project_shouldReadStreamWhenEventsAreDisabledBecauseCoreStatsAreAlwaysOn() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        AnalyticsRedisAggregateWriter aggregateWriter = mock(AnalyticsRedisAggregateWriter.class);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(AnalyticsKeys.visitEventStreamKey())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        MapRecord<String, Object, Object> record = visitRecord("1-0", Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record), List.of());
        acknowledgeAll(streamOps);

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                aggregateWriter
        );

        job.project();

        verify(redis).hasKey(AnalyticsKeys.visitEventStreamKey());
        verify(aggregateWriter).write(Map.of(
                "ts", String.valueOf(Instant.parse("2026-04-24T10:15:30Z").toEpochMilli()),
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        verify(streamOps).acknowledge(eq(AnalyticsKeys.visitEventStreamKey()), eq("lf-visit-projector"), any(RecordId[].class));
    }

    @Test
    void project_shouldTreatWrappedBusyGroupExceptionAsExistingGroup() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        AnalyticsRedisAggregateWriter aggregateWriter = mock(AnalyticsRedisAggregateWriter.class);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(AnalyticsKeys.visitEventStreamKey())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("Redis command failed", new RuntimeException("BUSYGROUP Consumer Group name already exists")));

        MapRecord<String, Object, Object> record = visitRecord("1-0", Map.of(
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        when(streamOps.read(any(org.springframework.data.redis.connection.stream.Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record), List.of());
        acknowledgeAll(streamOps);

        AnalyticsRedirectEventProjectorJob job = new AnalyticsRedirectEventProjectorJob(
                redis,
                properties,
                aggregateWriter
        );

        job.project();

        verify(aggregateWriter).write(Map.of(
                "tenantId", "1",
                "linkId", "10",
                "visitorKey", "visitor-1"
        ));
        verify(streamOps).acknowledge(eq(AnalyticsKeys.visitEventStreamKey()), eq("lf-visit-projector"), any(RecordId[].class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MapRecord<String, Object, Object> visitRecord(String id, Map<String, String> values) {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of(id));
        when(record.getValue()).thenReturn((Map) values);
        return record;
    }

    private static void acknowledgeAll(StreamOperations<String, Object, Object> streamOps) {
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class)))
                .thenAnswer(invocation -> (long) ((RecordId[]) invocation.getRawArguments()[2]).length);
    }
}
