package com.linkforge.analytics.infrastructure.job;

import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyUpsertRow;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsDimensionFlushJobTest {

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

