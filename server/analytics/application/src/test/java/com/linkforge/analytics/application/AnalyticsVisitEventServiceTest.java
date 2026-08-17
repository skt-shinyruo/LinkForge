package com.linkforge.analytics.application;

import com.linkforge.analytics.application.port.AnalyticsVisitEventAppender;
import com.linkforge.contract.analytics.RedirectVisitRecord;
import com.linkforge.contract.analytics.VisitContext;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.observability.OperationalMetrics;
import org.junit.jupiter.api.Test;

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
                "1.2.3.4",
                "Mozilla/5.0"
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
                new VisitContext(
                        "1.2.3.4",
                        "Mozilla/5.0"
                )
        ));

        verify(appender).append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                100L,
                200L,
                "1.2.3.4",
                "Mozilla/5.0"
        ));
    }

    @Test
    void append_should_swallow_appender_failure_when_fail_open_enabled() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        doThrow(new IllegalStateException("redis down")).when(appender).append(org.mockito.ArgumentMatchers.any());

        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(true);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties, metrics);

        assertThatCode(() -> service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                null,
                null,
                "1.2.3.4",
                "Mozilla/5.0"
        ))).doesNotThrowAnyException();

        verify(metrics).increment(
                "linkforge.analytics.fail_open",
                "component", "visit_appender",
                "reason", "appender"
        );
    }

    @Test
    void append_should_propagate_appender_failure_when_fail_open_disabled() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        doThrow(new IllegalStateException("redis down")).when(appender).append(org.mockito.ArgumentMatchers.any());

        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(false);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties, metrics);

        assertThatThrownBy(() -> service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L,
                10L,
                1_710_000_000_000L,
                null,
                null,
                "1.2.3.4",
                "Mozilla/5.0"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");
        verify(metrics).increment(
                "linkforge.analytics.degraded",
                "component", "visit_appender",
                "reason", "appender"
        );
    }

    @Test
    void append_shouldClassifyCapacityFailOpenWithoutUsingRawErrorAsTag() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        doThrow(new CapacityExceededException()).when(appender).append(org.mockito.ArgumentMatchers.any());
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(true);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties, metrics);

        service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L, 10L, 1_710_000_000_000L, null, null, null, null
        ));

        verify(metrics).increment(
                "linkforge.analytics.fail_open",
                "component", "visit_appender",
                "reason", "capacity"
        );
    }

    @Test
    void append_shouldPreferNestedCapacityReasonOverGenericRedisWrapper() {
        AnalyticsVisitEventAppender appender = mock(AnalyticsVisitEventAppender.class);
        OperationalMetrics metrics = mock(OperationalMetrics.class);
        doThrow(new RedisInfrastructureException(new IllegalStateException("OOM command not allowed")))
                .when(appender).append(org.mockito.ArgumentMatchers.any());
        AnalyticsProperties properties = new AnalyticsProperties();
        properties.getEvents().setFailOpen(true);
        AnalyticsVisitEventService service = new AnalyticsVisitEventService(appender, properties, metrics);

        service.append(new AnalyticsVisitEventService.RedirectVisitEvent(
                1L, 10L, 1_710_000_000_000L, null, null, null, null
        ));

        verify(metrics).increment(
                "linkforge.analytics.fail_open",
                "component", "visit_appender",
                "reason", "capacity"
        );
    }

    private static final class CapacityExceededException extends RuntimeException {
    }

    private static final class RedisInfrastructureException extends RuntimeException {
        private RedisInfrastructureException(Throwable cause) {
            super("redis operation failed", cause);
        }
    }
}
