package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyUpsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

class AnalyticsFlushJobTest {

    @Test
    void flush_shouldExposeAcknowledgedLegacyHistoryAsRetainedButNotRemaining() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String legacyKey = AnalyticsKeys.statsDirtyStreamKey(day);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> last = mock(MapRecord.class);
        PendingMessagesSummary pending = mock(PendingMessagesSummary.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(streams.size(legacyKey)).thenReturn(3L);
        when(streams.pending(legacyKey, "lf-stats-flush")).thenReturn(pending);
        when(pending.getTotalPendingMessages()).thenReturn(0L);
        when(redis.execute(any(RedisCallback.class))).thenReturn(List.of(List.of(
                "name".getBytes(StandardCharsets.UTF_8), "lf-stats-flush".getBytes(StandardCharsets.UTF_8),
                "lag".getBytes(StandardCharsets.UTF_8), 0L,
                "last-delivered-id".getBytes(StandardCharsets.UTF_8), "1-0".getBytes(StandardCharsets.UTF_8)
        )));
        when(last.getId()).thenReturn(RecordId.of(System.currentTimeMillis() - 1_000L, 0L));
        when(streams.reverseRange(eq(legacyKey), eq(Range.unbounded()), any(Limit.class)))
                .thenReturn(List.of(last));
        when(redis.hasKey(legacyKey)).thenReturn(false);

        new AnalyticsFlushJob(redis, mapper, null, properties, metrics).flush();

        verify(metrics).set("linkforge.analytics.dirty.legacy.retained_entries", 3L, "marker", "link");
        verify(metrics).set("linkforge.analytics.dirty.legacy.remaining", 0L, "marker", "link");
        verify(metrics).set(eq("linkforge.analytics.dirty.legacy.last_write_age_millis"),
                longThat(value -> value >= 0L), eq("marker"), eq("link"));
    }

    @Test
    void flush_shouldCompleteClaimedV2GenerationOnlyAfterSnapshotUpsert() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
        properties.getDirtyMarker().setLegacyReadEnabled(false);
        LocalDate day = LocalDate.now(ZoneOffset.UTC);

        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("1:10", "7"), List.of(1L, 0L), List.of());
        when(hashes.multiGet(
                AnalyticsKeys.statsDirtyMarkerV2FirstSeenKey(day),
                List.of((Object) "1:10")
        )).thenReturn(List.of(1_710_000_000_000L));

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(List.of(AnalyticsKeys.pvKey(1L, 10L, day)))).thenReturn(List.of("9"));
        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(4L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, properties);

        job.flush();

        InOrder inOrder = inOrder(mapper, redis);
        inOrder.verify(mapper).batchUpsert(anyList());
        inOrder.verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void flushDirtyMembers_should_deduplicate_hot_link_signals_within_a_batch() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            assertThat(keys).hasSize(1);
            return List.of("9");
        });
        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(4L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, properties);
        job.flushDirtyMembers(LocalDate.of(2026, 4, 24), List.of("1:10", "1:10", "1:10"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchUpsert(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(1);
    }

    @Test
    void flush_should_retry_pending_batch_after_database_failure_before_reading_new_messages() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
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
                .thenReturn(
                        List.of(),
                        (List) List.of(dirtyRecord),
                        (List) List.of(dirtyRecord),
                        List.of(),
                        List.of()
                );

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(List.of("7"));
        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L));
        when(mapper.batchUpsert(anyList()))
                .thenThrow(new DataAccessResourceFailureException("database down"))
                .thenReturn(1);

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, properties);

        job.flush();
        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId[].class));

        job.flush();

        verify(mapper, times(2)).batchUpsert(anyList());
        verify(streamOps).acknowledge(anyString(), eq("lf-stats-flush"), any(RecordId[].class));
    }

    @Test
    void flush_should_consume_dirty_members_from_stream_and_ack_after_successful_mysql_write() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
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
        Cursor<String> cursor = mock(Cursor.class);
        when(setOps.scan(anyString(), any())).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(List.of("7"));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, null, properties, metrics);

        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String streamKey = "stats:dirty:flush:" + day.format(DateTimeFormatter.BASIC_ISO_DATE);

        job.flush();

        verify(setOps, never()).scan(anyString(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<RecordId[]> ackCaptor = ArgumentCaptor.forClass(RecordId[].class);
        InOrder inOrder = inOrder(mapper, streamOps);
        inOrder.verify(mapper).batchUpsert(batchCaptor.capture());
        inOrder.verify(streamOps).acknowledge(eq(streamKey), eq("lf-stats-flush"), ackCaptor.capture());

        List<LinkStatsDailyUpsertRow> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(1);
        LinkStatsDailyUpsertRow row = batch.get(0);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getLinkId()).isEqualTo(10L);
        assertThat(row.getDay()).isEqualTo(day);
        assertThat(row.getPv()).isEqualTo(7L);
        assertThat(row.getUv()).isEqualTo(3L);
        assertThat(java.util.Arrays.stream(ackCaptor.getValue()).map(RecordId::toString).toList())
                .containsExactly("1-0");
        verify(metrics).add("linkforge.analytics.dirty.legacy.drained", 1L, "marker", "link");
    }

    @Test
    void flush_shouldNotReportLegacyDrainedWhenAckFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.setFlushBackfillDays(1);
        properties.getEvents().setPendingReclaimEnabled(false);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streams);
        when(redis.hasKey(anyString())).thenReturn(true);
        when(streams.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(new RuntimeException("BUSYGROUP Consumer Group name already exists"));
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        when(record.getValue()).thenReturn(Map.of());
        when(streams.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn((List) List.of(record));
        when(streams.acknowledge(anyString(), anyString(), any(RecordId[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        new AnalyticsFlushJob(redis, mapper, null, properties, metrics).flush();

        verify(streams).acknowledge(anyString(), eq("lf-stats-flush"), any(RecordId[].class));
        verify(metrics, never()).add(
                eq("linkforge.analytics.dirty.legacy.drained"),
                anyLong(),
                eq("marker"),
                eq("link")
        );
    }

    @Test
    void flushDirtyMembers_should_skip_rows_when_pv_key_missing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList("5", null));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L, 4L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, properties);

        LocalDate day = LocalDate.of(2026, 2, 19);
        job.flushDirtyMembers(day, List.of("1:10", "1:20"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchUpsert(batchCaptor.capture());

        List<LinkStatsDailyUpsertRow> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(1);

        LinkStatsDailyUpsertRow row = batch.get(0);
        assertThat(row.getLinkId()).isEqualTo(10L);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getDay()).isEqualTo(day);
        assertThat(row.getPv()).isEqualTo(5L);
        assertThat(row.getUv()).isEqualTo(3L);
    }

    @Test
    void flushDirtyMembers_should_not_skip_rows_when_uv_is_zero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(List.of("7"));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(0L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, properties);

        LocalDate day = LocalDate.of(2026, 2, 19);
        job.flushDirtyMembers(day, List.of("1:10"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LinkStatsDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchUpsert(batchCaptor.capture());

        List<LinkStatsDailyUpsertRow> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(1);

        LinkStatsDailyUpsertRow row = batch.get(0);
        assertThat(row.getLinkId()).isEqualTo(10L);
        assertThat(row.getTenantId()).isEqualTo(1L);
        assertThat(row.getDay()).isEqualTo(day);
        assertThat(row.getPv()).isEqualTo(7L);
        assertThat(row.getUv()).isZero();
    }

    @Test
    void flushDirtyScopeMembers_should_write_deduplicated_scope_uv_counts() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LinkStatsDailyMapper linkMapper = mock(LinkStatsDailyMapper.class);
        AnalyticsScopeStatsDailyMapper scopeMapper = mock(AnalyticsScopeStatsDailyMapper.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(1L, 2L, 3L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, linkMapper, scopeMapper, properties);

        LocalDate day = LocalDate.of(2026, 4, 24);
        job.flushDirtyScopeMembers(day, List.of(
                "tenant:1:0",
                "application:1:100",
                "domain:1:200",
                "application:1:100",
                "bad"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalyticsScopeStatsDailyUpsertRow>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(scopeMapper).batchUpsert(batchCaptor.capture());

        List<AnalyticsScopeStatsDailyUpsertRow> rows = batchCaptor.getValue();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(
                AnalyticsScopeStatsDailyUpsertRow::getScopeType,
                AnalyticsScopeStatsDailyUpsertRow::getTenantId,
                AnalyticsScopeStatsDailyUpsertRow::getScopeId,
                AnalyticsScopeStatsDailyUpsertRow::getDay,
                AnalyticsScopeStatsDailyUpsertRow::getUv
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("tenant", 1L, 0L, day, 1L),
                org.assertj.core.groups.Tuple.tuple("application", 1L, 100L, day, 2L),
                org.assertj.core.groups.Tuple.tuple("domain", 1L, 200L, day, 3L)
        );
    }
}
