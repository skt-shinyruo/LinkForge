package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernancePaginationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-08-15T10:00:00");

    @Test
    void approvalPages_shouldFetchOneExtraAndContinueFromStableTimeAndIdCursor() {
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        GovernanceService service = service(approvals, auditLogs);
        ApprovalRequestSummaryResult first = approval(103L, CREATED_AT);
        ApprovalRequestSummaryResult second = approval(102L, CREATED_AT);
        ApprovalRequestSummaryResult extra = approval(101L, CREATED_AT.minusSeconds(1));
        when(approvals.listSummaries(1L, ApprovalStatus.PENDING_APPROVAL, null, null, 3))
                .thenReturn(List.of(first, second, extra));

        GovernancePageResult<ApprovalRequestSummaryResult> firstPage =
                service.listRequests(1L, actor(), ApprovalStatus.PENDING_APPROVAL, 2, null);

        assertThat(firstPage.items()).containsExactly(first, second);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).startsWith("v1.");

        when(approvals.listSummaries(
                eq(1L),
                eq(ApprovalStatus.PENDING_APPROVAL),
                eq(CREATED_AT),
                eq(102L),
                eq(3)
        )).thenReturn(List.of(extra));

        GovernancePageResult<ApprovalRequestSummaryResult> secondPage = service.listRequests(
                1L,
                actor(),
                ApprovalStatus.PENDING_APPROVAL,
                2,
                firstPage.nextCursor()
        );

        assertThat(secondPage.items()).containsExactly(extra);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void auditPages_shouldReuseCursorAndLimitSemanticsWithFilters() {
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        GovernanceService service = service(approvals, auditLogs);
        AuditLogSummaryResult first = audit(203L, CREATED_AT);
        AuditLogSummaryResult second = audit(202L, CREATED_AT);
        AuditLogSummaryResult extra = audit(201L, CREATED_AT.minusSeconds(1));
        when(auditLogs.listSummaries(1L, "APPROVE_REQUEST", "approval_request", null, null, 3))
                .thenReturn(List.of(first, second, extra));

        GovernancePageResult<AuditLogSummaryResult> page = service.listAuditLogs(
                1L,
                actor(),
                " APPROVE_REQUEST ",
                " approval_request ",
                2,
                null
        );

        assertThat(page.items()).containsExactly(first, second);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).startsWith("v1.");
    }

    @Test
    void listPages_shouldRejectInvalidLimitsAndCursorsBeforeQueryingRepositories() {
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        GovernanceService service = service(approvals, auditLogs);

        assertThatThrownBy(() -> service.listRequests(1L, actor(), null, 0, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分页大小");
        assertThatThrownBy(() -> service.listAuditLogs(1L, actor(), null, null, 201, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分页大小");
        assertThatThrownBy(() -> service.listRequests(1L, actor(), null, null, "v2.invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分页游标无效");

        verify(approvals, never()).listSummaries(any(Long.class), any(), any(), any(), any(Integer.class));
        verify(auditLogs, never()).listSummaries(any(Long.class), any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void listPages_shouldUseSharedDefaultLimitOfFifty() {
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        GovernanceService service = service(approvals, auditLogs);
        when(approvals.listSummaries(1L, null, null, null, 51)).thenReturn(List.of());
        when(auditLogs.listSummaries(1L, null, null, null, null, 51)).thenReturn(List.of());

        service.listRequests(1L, actor(), null, null, null);
        service.listAuditLogs(1L, actor(), null, null, null, null);

        verify(approvals).listSummaries(1L, null, null, null, 51);
        verify(auditLogs).listSummaries(1L, null, null, null, null, 51);
    }

    private static GovernanceService service(
            ApprovalRepository approvals,
            AuditLogRepository auditLogs
    ) {
        return new GovernanceService(
                mock(SnowflakeIdGenerator.class),
                approvals,
                auditLogs,
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC),
                List.of()
        );
    }

    private static UserActor actor() {
        return new UserActor(1L, 7L, "admin@example.test", Set.of("TENANT_ADMIN"));
    }

    private static ApprovalRequestSummaryResult approval(long id, LocalDateTime createdAt) {
        return new ApprovalRequestSummaryResult(
                id,
                1L,
                SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                99L,
                7L,
                "requester@example.test",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                createdAt
        );
    }

    private static AuditLogSummaryResult audit(long id, LocalDateTime createdAt) {
        return new AuditLogSummaryResult(
                id,
                1L,
                7L,
                "admin@example.test",
                "APPROVE_REQUEST",
                "approval_request",
                "99",
                99L,
                createdAt
        );
    }
}
