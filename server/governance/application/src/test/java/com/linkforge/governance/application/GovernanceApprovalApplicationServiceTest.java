package com.linkforge.governance.application;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceApprovalApplicationServiceTest {

    @Test
    void requestLinkDestinationChangeApproval_shouldBuildVersionedStructuredPayloadInternally() {
        GovernanceService governanceService = mock(GovernanceService.class);
        ApprovalSubmissionPort service = new GovernanceApprovalApplicationService(governanceService);
        ApprovalRequester requester = new ApprovalRequester(1L, 7L, "reviewer@example.com");
        UserActor expectedActor = new UserActor(1L, 7L, "reviewer@example.com", Set.of());
        LocalDateTime requestedAt = LocalDateTime.parse("2026-04-01T00:00:00");

        when(governanceService.submitRequest(eq(1L), any(SubmitApprovalRequest.class)))
                .thenReturn(new ApprovalRequestResult(
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
                        requester,
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

        ArgumentCaptor<SubmitApprovalRequest> captor =
                ArgumentCaptor.forClass(SubmitApprovalRequest.class);
        verify(governanceService).submitRequest(eq(1L), captor.capture());
        SubmitApprovalRequest submitted = captor.getValue();
        assertThat(submitted.operationType()).isEqualTo(SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE);
        assertThat(submitted.targetApplicationId()).isEqualTo(2001L);
        assertThat(submitted.actor()).isEqualTo(expectedActor);
        assertThat(submitted.requestedAt()).isEqualTo(requestedAt);

        assertLinkDestinationPayload(submitted.beforeSnapshot(), 101L, "https://example.com/old");
        assertLinkDestinationPayload(submitted.afterSnapshot(), 101L, "https://example.com/new");
    }

    @Test
    void requestAnalyticsDetailExportApproval_shouldBuildVersionedStructuredPayloadInternally() {
        GovernanceService governanceService = mock(GovernanceService.class);
        ApprovalSubmissionPort service = new GovernanceApprovalApplicationService(governanceService);
        ApprovalRequester requester = new ApprovalRequester(1L, 9L, "tenant-admin@example.com");
        UserActor expectedActor = new UserActor(1L, 9L, "tenant-admin@example.com", Set.of());
        LocalDateTime from = LocalDateTime.parse("2026-04-05T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-04-06T00:00:00");
        LocalDateTime requestedAt = LocalDateTime.parse("2026-04-06T12:00:00");

        when(governanceService.submitRequest(eq(1L), any(SubmitApprovalRequest.class)))
                .thenReturn(new ApprovalRequestResult(
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
                        requester,
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

        ArgumentCaptor<SubmitApprovalRequest> captor =
                ArgumentCaptor.forClass(SubmitApprovalRequest.class);
        verify(governanceService).submitRequest(eq(1L), captor.capture());
        SubmitApprovalRequest submitted = captor.getValue();
        assertThat(submitted.operationType()).isEqualTo(SensitiveOperationType.ANALYTICS_DETAIL_EXPORT);
        assertThat(submitted.targetApplicationId()).isEqualTo(2001L);
        assertThat(submitted.beforeSnapshot()).isNull();
        assertThat(submitted.actor()).isEqualTo(expectedActor);
        assertThat(submitted.requestedAt()).isEqualTo(requestedAt);

        assertThat(submitted.afterSnapshot())
                .startsWith("{")
                .contains("\"type\":\"analyticsDetailExport\"")
                .contains("\"version\":1")
                .contains("\"linkId\":101")
                .contains("\"from\":\"" + from + "\"")
                .contains("\"to\":\"" + to + "\"");
    }

    private static void assertLinkDestinationPayload(String snapshot, long linkId, String originalUrl) {
        assertThat(snapshot)
                .startsWith("{")
                .contains("\"type\":\"linkDestinationChange\"")
                .contains("\"version\":1")
                .contains("\"linkId\":" + linkId)
                .contains("\"originalUrl\":\"" + originalUrl + "\"");
    }
}
