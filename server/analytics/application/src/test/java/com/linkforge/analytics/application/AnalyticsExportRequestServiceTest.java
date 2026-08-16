package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.context.UserActor;
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
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadPort,
                FIXED_CLOCK
        );

        when(shortLinkReadPort.findOwnership(1L, 101L)).thenReturn(Optional.empty());

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
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadPort,
                FIXED_CLOCK
        );

        when(shortLinkReadPort.findOwnership(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkReadPort.ShortLinkOwnership(3001L, null)));

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
        ApprovalSubmissionPort governanceApprovalRequestService = mock(ApprovalSubmissionPort.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                governanceApprovalRequestService,
                shortLinkReadPort,
                FIXED_CLOCK
        );
        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-04-05T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-04-06T00:00:00");
        ApprovalRequestView expected =
                new ApprovalRequestView(
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

        when(shortLinkReadPort.findOwnership(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkReadPort.ShortLinkOwnership(2001L, null)));
        when(governanceApprovalRequestService.requestAnalyticsDetailExportApproval(
                1L,
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        101L,
                        2001L,
                        from,
                        to,
                        new ApprovalRequester(1L, 9L, "tenant-admin@example.com"),
                        LocalDateTime.parse("2026-04-06T12:00:00")
                )
        )).thenReturn(expected);

        ApprovalRequestView actual =
                service.requestLinkEventExport(actor, 101L, 2001L, from, to);

        assertThat(actual).isSameAs(expected);
        verify(governanceApprovalRequestService).requestAnalyticsDetailExportApproval(
                1L,
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        101L,
                        2001L,
                        from,
                        to,
                        new ApprovalRequester(1L, 9L, "tenant-admin@example.com"),
                        LocalDateTime.parse("2026-04-06T12:00:00")
                )
        );
    }

    @Test
    void requestLinkEventExport_shouldRejectMoreThan366UtcDaysBeforeApproval() {
        ApprovalSubmissionPort approvalSubmissionPort = mock(ApprovalSubmissionPort.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsExportRequestService service = new AnalyticsExportRequestService(
                approvalSubmissionPort,
                shortLinkReadPort,
                FIXED_CLOCK
        );
        when(shortLinkReadPort.findOwnership(1L, 101L))
                .thenReturn(Optional.of(new ShortLinkReadPort.ShortLinkOwnership(2001L, null)));

        assertThatThrownBy(() -> service.requestLinkEventExport(
                new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN")),
                101L,
                2001L,
                LocalDateTime.parse("2024-01-01T00:00:00"),
                LocalDateTime.parse("2025-01-01T00:00:00")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
        verifyNoInteractions(approvalSubmissionPort);
    }
}
