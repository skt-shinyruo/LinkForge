package com.linkforge.analytics.application.job;

import com.linkforge.foundation.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsFlushJobTest {

    @Test
    void flushActiveMembers_should_skip_rows_when_pv_key_missing() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AppProperties properties = new AppProperties();

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(Arrays.asList("5", null));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(3L, 4L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, jdbcTemplate, properties);

        LocalDate day = LocalDate.of(2026, 2, 19);
        job.flushActiveMembers(day, List.of("1:10", "1:20"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), batchCaptor.capture());

        List<Object[]> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(1);

        Object[] row = batch.get(0);
        assertThat(row[0]).isEqualTo(10L);
        assertThat(row[1]).isEqualTo(1L);
        assertThat(row[2]).isEqualTo(Date.valueOf(day));
        assertThat(row[3]).isEqualTo(5L);
        assertThat(row[4]).isEqualTo(3L);
    }

    @Test
    void flushActiveMembers_should_skip_rows_when_uv_key_missing_or_zero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AppProperties properties = new AppProperties();

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(anyList())).thenReturn(List.of("7"));

        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(0L));

        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, jdbcTemplate, properties);

        LocalDate day = LocalDate.of(2026, 2, 19);
        job.flushActiveMembers(day, List.of("1:10"));

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }
}
