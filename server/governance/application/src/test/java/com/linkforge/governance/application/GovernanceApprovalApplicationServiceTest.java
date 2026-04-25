package com.linkforge.governance.application;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceApprovalApplicationServiceTest {

    @Test
    void requestLinkDestinationChangeApproval_shouldBuildGovernanceSubmissionInternally() {
        GovernanceService governanceService = mock(GovernanceService.class);
        ApprovalSubmissionPort service = new GovernanceApprovalApplicationService(governanceService);
        UserActor actor = new UserActor(1L, 7L, "reviewer@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime requestedAt = LocalDateTime.parse("2026-04-01T00:00:00");

        when(governanceService.submitRequest(
                eq(1L),
                argThat(req -> req.operationType() == SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE
                        && req.targetApplicationId().equals(2001L)
                        && "linkId=101\noriginalUrl=https://example.com/old".equals(req.beforeSnapshot())
                        && "linkId=101\noriginalUrl=https://example.com/new".equals(req.afterSnapshot())
                        && req.actor().equals(actor)
                        && requestedAt.equals(req.requestedAt()))
        )).thenReturn(new GovernanceService.ApprovalRequestDto(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "reviewer@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null
        ));

        ApprovalRequestView actual = service.requestLinkDestinationChangeApproval(
                1L,
                new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                        101L,
                        2001L,
                        "https://example.com/old",
                        "https://example.com/new",
                        actor,
                        requestedAt
                )
        );

        assertThat(actual).isEqualTo(new ApprovalRequestView(
                501L,
                1L,
                "PUBLIC_LINK_DESTINATION_CHANGE",
                2001L,
                7L,
                "reviewer@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));
        verify(governanceService).submitRequest(
                eq(1L),
                argThat(req -> req.operationType() == SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE
                        && req.targetApplicationId().equals(2001L)
                        && "linkId=101\noriginalUrl=https://example.com/old".equals(req.beforeSnapshot())
                        && "linkId=101\noriginalUrl=https://example.com/new".equals(req.afterSnapshot())
                        && req.actor().equals(actor)
                        && requestedAt.equals(req.requestedAt()))
        );
    }

    @Test
    void requestAnalyticsDetailExportApproval_shouldBuildGovernanceSubmissionInternally() {
        GovernanceService governanceService = mock(GovernanceService.class);
        ApprovalSubmissionPort service = new GovernanceApprovalApplicationService(governanceService);
        UserActor actor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of("TENANT_ADMIN"));
        LocalDateTime from = LocalDateTime.parse("2026-04-05T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-04-06T00:00:00");
        LocalDateTime requestedAt = LocalDateTime.parse("2026-04-06T12:00:00");

        when(governanceService.submitRequest(
                eq(1L),
                argThat(req -> req.operationType() == SensitiveOperationType.ANALYTICS_DETAIL_EXPORT
                        && req.targetApplicationId().equals(2001L)
                        && req.beforeSnapshot() == null
                        && "linkId=101,from=2026-04-05T00:00,to=2026-04-06T00:00".equals(req.afterSnapshot())
                        && req.actor().equals(actor)
                        && requestedAt.equals(req.requestedAt()))
        )).thenReturn(new GovernanceService.ApprovalRequestDto(
                502L,
                1L,
                SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                2001L,
                9L,
                "tenant-admin@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null
        ));

        ApprovalRequestView actual = service.requestAnalyticsDetailExportApproval(
                1L,
                new ApprovalSubmissionPort.AnalyticsDetailExportApprovalRequest(
                        101L,
                        2001L,
                        from,
                        to,
                        actor,
                        requestedAt
                )
        );

        assertThat(actual).isEqualTo(new ApprovalRequestView(
                502L,
                1L,
                "ANALYTICS_DETAIL_EXPORT",
                2001L,
                9L,
                "tenant-admin@example.com",
                "PENDING_APPROVAL",
                null,
                null,
                null
        ));
        verify(governanceService).submitRequest(
                eq(1L),
                argThat(req -> req.operationType() == SensitiveOperationType.ANALYTICS_DETAIL_EXPORT
                        && req.targetApplicationId().equals(2001L)
                        && req.beforeSnapshot() == null
                        && "linkId=101,from=2026-04-05T00:00,to=2026-04-06T00:00".equals(req.afterSnapshot())
                        && req.actor().equals(actor)
                        && requestedAt.equals(req.requestedAt()))
        );
    }
}
