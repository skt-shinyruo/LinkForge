package com.linkforge.analytics.application;

import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalyticsVisitEventServiceTest {

    @Test
    void append_should_delegate_event_to_appender() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, new AnalyticsProperties());
        AnalyticsVisitEventService.RedirectVisitEvent event = new AnalyticsVisitEventService.RedirectVisitEvent(
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

        service.append(event);

        verify(appender).append(event);
    }

    @Test
    void recordVisit_should_convert_contract_record_and_delegate_to_appender() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, new AnalyticsProperties());

        service.recordVisit(new RedirectVisitRecord(
                1L,
                10L,
                1_710_000_000_000L,
                100L,
                200L,
                "abc123",
                "https://example.com/live",
                new VisitContext(
                        "1.2.3.4",
                        "Mozilla/5.0",
                        "https://example.com/ref",
                        "zh-CN,zh;q=0.9",
                        Map.of("utm_source", "newsletter")
                )
        ));

        verify(appender).append(new AnalyticsVisitEventService.RedirectVisitEvent(
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
        ));
    }

    @Test
    void append_should_swallow_appender_failure_when_fail_open_enabled() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        doThrow(new IllegalStateException("redis down")).when(appender).append(org.mockito.ArgumentMatchers.any());

        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(true);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties);

        assertThatCode(() -> service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                null,
                null,
                "abc123",
                "https://example.com/live",
                "1.2.3.4",
                "Mozilla/5.0",
                null,
                null,
                Map.of()
        ))).doesNotThrowAnyException();
    }

    @Test
    void append_should_propagate_appender_failure_when_fail_open_disabled() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        doThrow(new IllegalStateException("redis down")).when(appender).append(org.mockito.ArgumentMatchers.any());

        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(false);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties);

        assertThatThrownBy(() -> service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                null,
                null,
                "abc123",
                "https://example.com/live",
                "1.2.3.4",
                "Mozilla/5.0",
                null,
                null,
                Map.of()
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");
    }
}
