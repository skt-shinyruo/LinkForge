package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.IdProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsEventIngestJobPoisonIsolationTest {

    @Test
    void ingestRecords_should_isolate_poison_record_and_ack_to_avoid_pending_stuck() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkVisitEventMapper mapper = mock(LinkVisitEventMapper.class);
        AnalyticsProperties analyticsProperties = new AnalyticsProperties();
        analyticsProperties.getEvents().setEnabled(true);
        analyticsProperties.getEvents().setSampleRate(1);
        IdProperties idProperties = new IdProperties();
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1L, 1L);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);
        when(streamOps.add(any())).thenReturn(RecordId.of("0-0"));
        when(streamOps.trim(anyString(), anyLong(), eq(true))).thenReturn(1L);

        when(mapper.batchInsertIgnore(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<LinkVisitEventInsertRow> rows = (List<LinkVisitEventInsertRow>) invocation.getArgument(0);
            if (rows.size() == 2) {
                throw new DataIntegrityViolationException("batch failed");
            }
            String requestId = rows.get(0).getRequestId();
            if ("req-ok".equals(requestId)) {
                return 1;
            }
            if ("req-bad".equals(requestId)) {
                throw new DataIntegrityViolationException("row failed");
            }
            return 1;
        });

        AnalyticsEventIngestJob job = new AnalyticsEventIngestJob(
                redis,
                mapper,
                analyticsProperties,
                idProperties,
                idGenerator
        );

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> r1 = mock(MapRecord.class);
        when(r1.getId()).thenReturn(RecordId.of("1-0"));
        when(r1.getValue()).thenReturn((Map) Map.of(
                "tenantId", "1",
                "linkId", "10",
                "requestId", "req-ok",
                "ts", "1710000000000"
        ));

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> r2 = mock(MapRecord.class);
        when(r2.getId()).thenReturn(RecordId.of("2-0"));
        when(r2.getValue()).thenReturn((Map) Map.of(
                "tenantId", "1",
                "linkId", "11",
                "requestId", "req-bad",
                "ts", "1710000000000"
        ));

        String streamKey = "stats:visit:events";
        job.ingestRecords(streamKey, List.of(r1, r2));

        // Both records should be acked (ok row inserted; bad row dead-lettered) so pending does not get stuck.
        ArgumentCaptor<RecordId[]> idsCaptor = ArgumentCaptor.forClass(RecordId[].class);
        verify(streamOps, atLeastOnce()).acknowledge(eq(streamKey), eq("lf-visit-ingest"), idsCaptor.capture());

        List<String> allAcked = idsCaptor.getAllValues().stream()
                .flatMap(arr -> Arrays.stream(arr))
                .map(RecordId::toString)
                .toList();
        assertThat(allAcked).contains("1-0", "2-0");

        verify(streamOps).add(any());
        verify(streamOps).trim(eq(streamKey + ":dlq"), eq(10_000L), eq(true));
    }
}
