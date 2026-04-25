package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.contract.analytics.AnalyticsKeys;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisAnalyticsVisitEventAppenderTest {

    @Test
    void append_shouldSkipRedisWhenEventsAreDisabled() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verifyNoInteractions(redis);
    }

    @Test
    void append_shouldSkipRedisWhenSampleRateIsZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verifyNoInteractions(redis);
    }

    @Test
    void append_shouldWriteStreamWhenEventsEnabledAndSampleRateIsOne() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);
        properties.getEvents().setStreamMaxLen(500);

        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        when(streamOps.add(any())).thenReturn(RecordId.of("1-0"));

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(redis, properties);

        appender.append(event());

        verify(streamOps).add(any());
        verify(streamOps).trim(eq(AnalyticsKeys.visitEventStreamKey()), eq(500L), eq(true));
    }

    private static AnalyticsVisitEventService.RedirectVisitEvent event() {
        return new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                100L,
                200L,
                "abc123",
                "https://example.com/live",
                "1.2.3.4",
                "Mozilla/5.0",
                "https://example.com/ref",
                "zh-CN,zh;q=0.9",
                Map.of("utm_source", "newsletter")
        );
    }
}
