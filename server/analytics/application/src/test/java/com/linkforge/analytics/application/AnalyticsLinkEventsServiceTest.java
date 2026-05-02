package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsLinkEventsServiceTest {

    @Test
    void listLinkEvents_shouldDefaultWindowAndLimitBeforeQuerying() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsLinkEventsService service = new AnalyticsLinkEventsService(
                queryService,
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime expectedTo = LocalDateTime.parse("2026-04-06T12:00:00");
        LocalDateTime expectedFrom = expectedTo.minusDays(1);
        List<AnalyticsQueryService.VisitEvent> expected = List.of();
        when(queryService.linkEvents(1L, 101L, expectedFrom, expectedTo, 50)).thenReturn(expected);

        assertThat(service.listLinkEvents(actor, 101L, null, null, null)).isSameAs(expected);

        verify(queryService).linkEvents(1L, 101L, expectedFrom, expectedTo, 50);
    }

    @Test
    void listLinkEvents_shouldRejectInvalidLimit() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        AnalyticsLinkEventsService service = new AnalyticsLinkEventsService(
                queryService,
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.listLinkEvents(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                null,
                null,
                0
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void listLinkEvents_shouldRejectNonAdminActor() {
        AnalyticsLinkEventsService service = new AnalyticsLinkEventsService(
                mock(AnalyticsQueryService.class),
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.listLinkEvents(
                new UserActor(1L, 9L, "user@example.com", Set.of("USER")),
                101L,
                null,
                null,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void listLinkEvents_shouldRejectOversizedTimeRange() {
        AnalyticsLinkEventsService service = new AnalyticsLinkEventsService(
                mock(AnalyticsQueryService.class),
                Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.listLinkEvents(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                LocalDateTime.parse("2026-03-01T00:00:00"),
                LocalDateTime.parse("2026-03-10T00:00:00"),
                50
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
