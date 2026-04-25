package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.application.GovernanceApprovalRequestService;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsExportRequestServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-06T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void requestLinkEventExport_shouldCheckMissingLinkBeforeDateValidation() {
        GovernanceApprovalRequestService governanceApprovalRequestService = mock(GovernanceApprovalRequestService.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadService,
                FIXED_CLOCK
        );

        when(shortLinkReadService.findOwnership(1L, 101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestLinkEventExport(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                2001L,
                LocalDateTime.parse("2026-04-07T00:00:00"),
                LocalDateTime.parse("2026-04-06T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(governanceApprovalRequestService);
    }

    @Test
    void requestLinkEventExport_shouldCheckApplicationScopeBeforeDateValidation() {
        GovernanceApprovalRequestService governanceApprovalRequestService = mock(GovernanceApprovalRequestService.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadService,
                FIXED_CLOCK
        );

        when(shortLinkReadService.findOwnership(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkReadService.LinkOwnership(3001L, null)));

        assertThatThrownBy(() -> service.requestLinkEventExport(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                2001L,
                LocalDateTime.parse("2026-04-07T00:00:00"),
                LocalDateTime.parse("2026-04-06T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(governanceApprovalRequestService);
    }

    @Test
    void requestLinkEventExport_shouldRequestApprovalThroughNarrowGovernanceApi() {
        GovernanceApprovalRequestService governanceApprovalRequestService = mock(GovernanceApprovalRequestService.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadService,
                FIXED_CLOCK
        );
        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-04-05T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-04-06T00:00:00");
        GovernanceApprovalRequestService.ApprovalRequestResult expected =
                new GovernanceApprovalRequestService.ApprovalRequestResult(
                        501L,
                        1L,
                        "ANALYTICS_DETAIL_EXPORT",
                        2001L,
                        9L,
                        "tenant-admin@example.com",
                        "PENDING_APPROVAL",
                        null,
                        null,
                        null
                );

        when(shortLinkReadService.findOwnership(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkReadService.LinkOwnership(2001L, null)));
        when(governanceApprovalRequestService.requestAnalyticsDetailExportApproval(
                1L,
                new GovernanceApprovalRequestService.AnalyticsDetailExportApprovalRequest(
                        101L,
                        2001L,
                        from,
                        to,
                        actor,
                        LocalDateTime.parse("2026-04-06T12:00:00")
                )
        )).thenReturn(expected);

        GovernanceApprovalRequestService.ApprovalRequestResult actual =
                service.requestLinkEventExport(actor, 101L, 2001L, from, to);

        assertThat(actual).isSameAs(expected);
        verify(governanceApprovalRequestService).requestAnalyticsDetailExportApproval(
                1L,
                new GovernanceApprovalRequestService.AnalyticsDetailExportApprovalRequest(
                        101L,
                        2001L,
                        from,
                        to,
                        actor,
                        LocalDateTime.parse("2026-04-06T12:00:00")
                )
        );
    }
}
