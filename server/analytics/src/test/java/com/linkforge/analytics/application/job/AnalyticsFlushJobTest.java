package com.linkforge.analytics.application.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsFlushJobTest {

    @Test
    void flushActiveMembers_should_skip_rows_when_pv_key_missing() {
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
        job.flushActiveMembers(day, List.of("1:10", "1:20"));

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
    void flushActiveMembers_should_skip_rows_when_uv_key_missing_or_zero() {
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
        job.flushActiveMembers(day, List.of("1:10"));

        verify(mapper, never()).batchUpsert(any(List.class));
    }
}
