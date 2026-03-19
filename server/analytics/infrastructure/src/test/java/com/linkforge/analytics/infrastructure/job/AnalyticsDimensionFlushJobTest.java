package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyUpsertRow;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsDimensionFlushJobTest {

    @Test
    void flush_should_consume_dimension_dirty_members_from_its_own_stream_and_ack_after_successful_write() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDimDailyMapper mapper = mock(LinkStatsDimDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));
        properties.getDimensions().setMaxLinksPerDay(10);
        properties.getEvents().setPendingReclaimEnabled(false);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(anyString())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> dirtyRecord = mock(MapRecord.class);
        when(dirtyRecord.getId()).thenReturn(RecordId.of("1-0"));
        when(dirtyRecord.getValue()).thenReturn((Map) Map.of("member", "1:10"));

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(dirtyRecord), List.of());
        when(streamOps.acknowledge(anyString(), anyString(), any(RecordId[].class))).thenReturn(1L);

        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        @SuppressWarnings("unchecked")
        Cursor<String> setCursor = mock(Cursor.class);
        when(setOps.scan(anyString(), any())).thenReturn(setCursor);
        when(setCursor.hasNext()).thenReturn(false);

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L));

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        @SuppressWarnings("unchecked")
        Cursor<Map.Entry<Object, Object>> hashCursor = mock(Cursor.class);
        when(hashOps.scan(anyString(), any())).thenReturn(hashCursor);
        when(hashCursor.hasNext()).thenReturn(true, false);
        when(hashCursor.next()).thenReturn(Map.entry("example.com", "5"));

        AnalyticsDimensionFlushJob job = new AnalyticsDimensionFlushJob(redis, mapper, properties);

        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String streamKey = "stats:dirty:dim:" + day.format(DateTimeFormatter.BASIC_ISO_DATE);

        job.flush();

        verify(setOps, never()).scan(anyString(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDimDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RecordId[]> ackCaptor = ArgumentCaptor.forClass(RecordId[].class);
        InOrder inOrder = inOrder(mapper, streamOps);
        inOrder.verify(mapper).batchUpsert(batchCaptor.capture());
        inOrder.verify(streamOps).acknowledge(eq(streamKey), eq("lf-dim-flush"), ackCaptor.capture());

        List<LinkStatsDimDailyUpsertRow> rows = batchCaptor.getValue();
        assertThat(rows).hasSize(1);

        LinkStatsDimDailyUpsertRow row = rows.get(0);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getLinkId()).isEqualTo(10L);
        assertThat(row.getDay()).isEqualTo(day);
        assertThat(row.getDimType()).isEqualTo("referer_domain");
        assertThat(row.getDimValue()).isEqualTo("example.com");
        assertThat(row.getPv()).isEqualTo(5L);
        assertThat(row.getUv()).isEqualTo(3L);
        assertThat(java.util.Arrays.stream(ackCaptor.getValue()).map(RecordId::toString).toList())
                .containsExactly("1-0");
    }

    @Test
    void flush_should_not_ack_dirty_record_when_dim_hash_scan_fails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDimDailyMapper mapper = mock(LinkStatsDimDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(redis.hasKey(anyString())).thenReturn(true);
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> dirtyRecord = mock(MapRecord.class);
        when(dirtyRecord.getId()).thenReturn(RecordId.of("1-0"));
        when(dirtyRecord.getValue()).thenReturn((Map) Map.of("member", "1:10"));

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(dirtyRecord), List.of());

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.scan(anyString(), any())).thenThrow(new RuntimeException("redis scan failed"));

        AnalyticsDimensionFlushJob job = new AnalyticsDimensionFlushJob(redis, mapper, properties);

        job.flush();

        verify(mapper, never()).batchUpsert(any());
        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId[].class));
    }

    @Test
    void flushActiveMembers_should_surface_member_failure_as_retryable_batch_failure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDimDailyMapper mapper = mock(LinkStatsDimDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        @SuppressWarnings("unchecked")
        Cursor<Map.Entry<Object, Object>> cursor = mock(Cursor.class);
        when(hashOps.scan(anyString(), any())).thenReturn(cursor);
        when(cursor.hasNext()).thenThrow(new RuntimeException("redis cursor failed"));

        AnalyticsDimensionFlushJob job = new AnalyticsDimensionFlushJob(redis, mapper, properties);

        boolean flushed = job.flushActiveMembers(LocalDate.of(2026, 2, 19), properties.getDimensions(), List.of("1:10"));

        assertThat(flushed).isFalse();
        verify(mapper, never()).batchUpsert(any());
    }

    @Test
    void flushActiveMembers_should_compute_uv_by_pfcount_and_write_to_mysql() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDimDailyMapper mapper = mock(LinkStatsDimDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getDimensions().setEnabled(true);
        properties.getDimensions().setTypes(List.of("referer_domain"));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L));

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        @SuppressWarnings("unchecked")
        Cursor<Map.Entry<Object, Object>> cursor = mock(Cursor.class);
        when(hashOps.scan(anyString(), any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(Map.entry("example.com", "5"));

        AnalyticsDimensionFlushJob job = new AnalyticsDimensionFlushJob(redis, mapper, properties);

        LocalDate day = LocalDate.of(2026, 2, 19);
        job.flushActiveMembers(day, properties.getDimensions(), List.of("1:10"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDimDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchUpsert(batchCaptor.capture());

        List<LinkStatsDimDailyUpsertRow> rows = batchCaptor.getValue();
        assertThat(rows).hasSize(1);

        LinkStatsDimDailyUpsertRow row = rows.get(0);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getLinkId()).isEqualTo(10L);
        assertThat(row.getDay()).isEqualTo(day);
        assertThat(row.getDimType()).isEqualTo("referer_domain");
        assertThat(row.getDimValue()).isEqualTo("example.com");
        assertThat(row.getPv()).isEqualTo(5L);
        assertThat(row.getUv()).isEqualTo(3L);
    }
}
