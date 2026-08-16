package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalyticsVisitEventAppenderTest {

    @Test
    void append_shouldUseOneApproximateMaxLenXaddWithDefaultCapacity() {
        RedisBoundary boundary = redisBoundary();
        AnalyticsProperties properties = new AnalyticsProperties();

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(boundary.redis(), properties);

        appender.append(event());

        RedisStreamCommands.XAddOptions options = captureXaddOptions(boundary);
        assertThat(options.hasMaxlen()).isTrue();
        assertThat(options.getMaxlen()).isEqualTo(200_000L);
        assertThat(options.isApproximateTrimming()).isTrue();
        verify(boundary.redis(), times(1)).execute(any(RedisCallback.class));
    }

    @Test
    void append_shouldWriteStreamWhenEventsAreDisabled() {
        RedisBoundary boundary = redisBoundary();
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(false);

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(boundary.redis(), properties);

        appender.append(event());

        assertThat(captureXaddOptions(boundary).getMaxlen()).isEqualTo(200_000L);
    }

    @Test
    void append_shouldWriteStreamWhenDetailSampleRateIsZero() {
        RedisBoundary boundary = redisBoundary();
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(0);
        properties.getEvents().setStreamMaxLen(0);

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(boundary.redis(), properties);

        appender.append(event());

        assertThat(captureXaddOptions(boundary).hasMaxlen()).isFalse();
    }

    @Test
    void append_shouldWriteStreamWhenEventsEnabledAndSampleRateIsOne() {
        RedisBoundary boundary = redisBoundary();
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setEnabled(true);
        properties.getEvents().setSampleRate(1);
        properties.getEvents().setStreamMaxLen(500);

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(boundary.redis(), properties);

        appender.append(event());

        assertThat(captureXaddOptions(boundary).getMaxlen()).isEqualTo(500L);
    }

    @Test
    void append_shouldPreferDedicatedVisitStreamMaxLenOverLegacyEventsStreamMaxLen() {
        RedisBoundary boundary = redisBoundary();
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setStreamMaxLen(500);
        properties.getVisitStream().setMaxLen(900L);

        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(boundary.redis(), properties);

        appender.append(event());

        assertThat(captureXaddOptions(boundary).getMaxlen()).isEqualTo(900L);
    }

    private static RedisBoundary redisBoundary() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStreamCommands streamCommands = mock(RedisStreamCommands.class);
        when(connection.streamCommands()).thenReturn(streamCommands);
        when(redis.getStringSerializer()).thenReturn(new StringRedisSerializer());
        when(redis.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RedisCallback<Object> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        return new RedisBoundary(redis, streamCommands);
    }

    private static RedisStreamCommands.XAddOptions captureXaddOptions(RedisBoundary boundary) {
        ArgumentCaptor<RedisStreamCommands.XAddOptions> options =
                ArgumentCaptor.forClass(RedisStreamCommands.XAddOptions.class);
        verify(boundary.streamCommands()).xAdd(any(MapRecord.class), options.capture());
        return options.getValue();
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

    private record RedisBoundary(StringRedisTemplate redis, RedisStreamCommands streamCommands) {
    }
}
