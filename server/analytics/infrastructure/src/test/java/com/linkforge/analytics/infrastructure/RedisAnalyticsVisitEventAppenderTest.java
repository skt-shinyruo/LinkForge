package com.linkforge.analytics.infrastructure;

import com.linkforge.analytics.application.AnalyticsVisitEventService;
import com.linkforge.analytics.infrastructure.job.AnalyticsRedisAggregateWriter;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisAnalyticsVisitEventAppenderTest {

    @Test
    void append_shouldSendOneMinimalAggregateEvent() {
        AnalyticsRedisAggregateWriter writer = mock(AnalyticsRedisAggregateWriter.class);
        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(writer, new AnalyticsProperties());

        appender.append(event());

        ArgumentCaptor<String> visitor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestId = ArgumentCaptor.forClass(String.class);
        verify(writer).write(
                eq(1L), eq(10L), eq(1_710_000_000_000L), eq(100L), eq(200L),
                visitor.capture(), requestId.capture()
        );
        assertThat(visitor.getValue()).isNotBlank();
        assertThat(requestId.getValue()).isNotBlank();
    }

    @Test
    void append_shouldNotWriteAnythingForNullEvent() {
        AnalyticsRedisAggregateWriter writer = mock(AnalyticsRedisAggregateWriter.class);
        RedisAnalyticsVisitEventAppender appender = new RedisAnalyticsVisitEventAppender(writer, new AnalyticsProperties());

        appender.append(null);

        org.mockito.Mockito.verifyNoInteractions(writer);
    }

    private static AnalyticsVisitEventService.RedirectVisitEvent event() {
        return new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                100L,
                200L,
                "1.2.3.4",
                "Mozilla/5.0"
        );
    }
}
