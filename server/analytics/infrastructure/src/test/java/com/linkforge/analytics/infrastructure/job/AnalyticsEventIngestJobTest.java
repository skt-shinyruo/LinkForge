package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsEventIngestJobTest {

    @Test
    void ingest_shouldSkipRedisWhenDetailEventsAreDisabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingest();

        verifyNoInteractions(redis);
        verifyNoInteractions(mapper);
    }

    @Test
    void ingest_shouldDrainSeveralBatchesButStopAtConfiguredFairnessLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);
        properties.getEvents().setPendingReclaimEnabled(false);
        properties.getEvents().setIngestBatchSize(2);
        properties.getEvents().setIngestMaxBatches(2);
        properties.getEvents().setIngestTimeBudgetMs(5_000);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.hasKey("stats:visit:events")).thenReturn(true);
        when(redis.opsForStream()).thenReturn(streamOps);
        acknowledgeAll(streamOps);
        List<MapRecord<String, Object, Object>> firstBatch = List.of(
                record("1-0", "1", "10", "req-1"), record("2-0", "1", "10", "req-2")
        );
        List<MapRecord<String, Object, Object>> secondBatch = List.of(
                record("3-0", "1", "10", "req-3"), record("4-0", "1", "10", "req-4")
        );
        when(streamOps.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(StreamReadOptions.class),
                any(StreamOffset.class)
        )).thenReturn(
                List.of(),
                firstBatch,
                List.of(),
                secondBatch
        );
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(2L);
        when(mapper.batchInsertIgnore(anyList())).thenReturn(2);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingest();

        verify(mapper, times(2)).batchInsertIgnore(anyList());
    }

    @Test
    void ingestRecords_shouldInsertAllValidRowsWhenSampleRateIsOne() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        acknowledgeAll(streamOps);
        when(mapper.batchInsertIgnore(anyList())).thenReturn(2);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "1", "11", "req-2")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkVisitEventInsertRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertIgnore(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).extracting(LinkVisitEventInsertRow::getRequestId)
                .containsExactly("req-1", "req-2");

        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    @Test
    void ingestRecords_shouldStopCurrentDrainWhenAckIsPartial() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);
        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        boolean completed = job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "1", "11", "req-2")
        ));

        assertThat(completed).isFalse();
    }

    @Test
    void ingestRecords_shouldAckValidRowsWithoutInsertWhenSampleRateIsZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        acknowledgeAll(streamOps);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "1", "11", "req-2")
        ));

        verify(mapper, never()).batchInsertIgnore(anyList());
        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    @Test
    void ingestRecords_shouldAckInvalidRowsAndSampledOutValidRowsTogether() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        acknowledgeAll(streamOps);

        AnalyticsEventIngestJob job = job(redis, mapper, properties);

        job.ingestRecords("stats:visit:events", List.of(
                record("1-0", "1", "10", "req-1"),
                record("2-0", "-1", "0", "req-invalid")
        ));

        verify(mapper, never()).batchInsertIgnore(anyList());
        assertAcked(streamOps, "stats:visit:events", "1-0", "2-0");
    }

    private static AnalyticsEventIngestJob job(
            StringRedisTemplate redis,
            LinkVisitEventMapper mapper,
            AnalyticsProperties properties
    ) {
        return new AnalyticsEventIngestJob(
                redis,
                mapper,
                properties,
                new IdProperties(),
                new SnowflakeIdGenerator(1L, 1L)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MapRecord<String, Object, Object> record(String id, String tenantId, String linkId, String requestId) {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of(id));
        when(record.getValue()).thenReturn((Map) Map.of(
                "tenantId", tenantId,
                "linkId", linkId,
                "requestId", requestId,
                "ts", "1710000000000"
        ));
        return record;
    }

    private static void assertAcked(
            StreamOperations<String, Object, Object> streamOps,
            String streamKey,
            String... expectedIds
    ) {
        ArgumentCaptor<RecordId[]> idsCaptor = ArgumentCaptor.forClass(RecordId[].class);
        verify(streamOps).acknowledge(eq(streamKey), eq("lf-visit-ingest"), idsCaptor.capture());

        List<String> actual = Arrays.stream(idsCaptor.getValue())
                .map(RecordId::toString)
                .toList();
        assertThat(actual).containsExactlyInAnyOrder(expectedIds);
    }

    private static void acknowledgeAll(StreamOperations<String, Object, Object> streamOps) {
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class)))
                .thenAnswer(invocation -> (long) ((RecordId[]) invocation.getRawArguments()[2]).length);
    }
}
