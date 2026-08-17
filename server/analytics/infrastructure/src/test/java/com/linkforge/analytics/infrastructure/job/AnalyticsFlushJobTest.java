package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsScopeStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsFlushJobTest {

    @Test
    void flushDirtyMembers_shouldWriteCurrentRedisSnapshot() {
        StringRedisTemplate redis = mockRedis("7");
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, new AnalyticsProperties());

        assertThat(job.flushDirtyMembers(LocalDate.of(2026, 8, 17), List.of("1:10"))).isTrue();

        verify(mapper).batchUpsert(anyList());
    }

    @Test
    void flushDirtyMembers_shouldKeepFailureRetryable() {
        StringRedisTemplate redis = mockRedis("7");
        LinkStatsDailyMapper mapper = mock(LinkStatsDailyMapper.class);
        when(mapper.batchUpsert(anyList())).thenThrow(new DataAccessResourceFailureException("database down"));
        AnalyticsFlushJob job = new AnalyticsFlushJob(redis, mapper, new AnalyticsProperties());

        assertThat(job.flushDirtyMembers(LocalDate.of(2026, 8, 17), List.of("1:10"))).isFalse();
    }

    private static StringRedisTemplate mockRedis(String pv) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(pv == null ? List.of() : List.of(pv));
        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(2L));
        return redis;
    }
}
