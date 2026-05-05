package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalPayloads;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.governance.application.port.ApprovalRepository;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GovernanceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-04-01T12:00:00");

    @Test
    void approveRequest_shouldRejectNonPendingRequestBeforeExecutionAndAudit() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of(executionPort));
        ApprovalRequest executed = request(ApprovalStatus.EXECUTED);
        when(approvalRepository.findByTenantIdAndId(1L, 501L)).thenReturn(Optional.of(executed));

        assertThatThrownBy(() -> service.approveRequest(1L, 501L, "again", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批请求状态已变化");

        verifyNoInteractions(executionPort, auditLogRepository);
        verify(approvalRepository, never()).markApprovedIfPending(anyLong(), anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void approveRequest_shouldRejectStaleClaimBeforeExecutionAndAudit() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of(executionPort));
        ApprovalRequest pending = request(ApprovalStatus.PENDING_APPROVAL);
        when(approvalRepository.findByTenantIdAndId(1L, 501L)).thenReturn(Optional.of(pending));
        when(executionPort.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).thenReturn(true);
        when(approvalRepository.markApprovedIfPending(1L, 501L, 8L, "approver@example.com", "ok", NOW))
                .thenReturn(false);

        assertThatThrownBy(() -> service.approveRequest(1L, 501L, "ok", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批请求状态已变化");

        verify(executionPort).supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE);
        verify(executionPort, never()).execute(any(), any());
        verifyNoInteractions(auditLogRepository);
        verify(approvalRepository, never()).markExecutedIfApproved(anyLong(), anyLong(), any());
    }

    @Test
    void approveRequest_shouldClaimExecuteMarkExecutedAndAuditForSupportedApproval() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of(executionPort));
        ApprovalRequest pending = request(ApprovalStatus.PENDING_APPROVAL);
        ApprovalRequest executed = decidedRequest(ApprovalStatus.EXECUTED, NOW, NOW);
        when(approvalRepository.findByTenantIdAndId(1L, 501L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(executed));
        when(executionPort.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).thenReturn(true);
        when(approvalRepository.markApprovedIfPending(1L, 501L, 8L, "approver@example.com", "ok", NOW))
                .thenReturn(true);
        when(approvalRepository.markExecutedIfApproved(1L, 501L, NOW)).thenReturn(true);

        ApprovalRequestResult actual =
                service.approveRequest(1L, 501L, "ok", approver(), NOW);

        assertThat(actual.status()).isEqualTo(ApprovalStatus.EXECUTED);
        InOrder inOrder = inOrder(approvalRepository, executionPort, auditLogRepository);
        inOrder.verify(approvalRepository).markApprovedIfPending(1L, 501L, 8L, "approver@example.com", "ok", NOW);
        inOrder.verify(executionPort).execute(
                argThat(request -> request.equals(new ApprovalExecutionRequest(
                        501L,
                        1L,
                        SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                        2001L,
                        linkDestinationPayload(101L, "https://example.com/old"),
                        linkDestinationPayload(101L, "https://example.com/new")
                ))),
                eq(NOW)
        );
        inOrder.verify(approvalRepository).markExecutedIfApproved(1L, 501L, NOW);
        inOrder.verify(auditLogRepository).insert(any(AuditLog.class));
    }

    @Test
    void approveRequest_shouldRemainApprovedWhenNoExecutorSupportsOperation() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of());
        ApprovalRequest pending = decisionOnlyRequest(ApprovalStatus.PENDING_APPROVAL);
        ApprovalRequest approved = decisionOnlyDecidedRequest(ApprovalStatus.APPROVED, NOW, null);
        when(approvalRepository.findByTenantIdAndId(1L, 502L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(approved));
        when(approvalRepository.markApprovedIfPending(1L, 502L, 8L, "approver@example.com", "ok", NOW))
                .thenReturn(true);

        ApprovalRequestResult actual =
                service.approveRequest(1L, 502L, "ok", approver(), NOW);

        assertThat(actual.status()).isEqualTo(ApprovalStatus.APPROVED);
        verify(approvalRepository).markApprovedIfPending(1L, 502L, 8L, "approver@example.com", "ok", NOW);
        verify(approvalRepository, never()).markExecutedIfApproved(anyLong(), anyLong(), any());
        verify(auditLogRepository).insert(any(AuditLog.class));
    }

    @Test
    void approveRequest_shouldEnforceQuotaCeilingFromStructuredPayload() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of());
        ApprovalRequest pending = quotaRequest("""
                {"type":"applicationQuotaIncrease","version":1,"monthlyLinkLimit":250000,"monthlyClickLimit":1000000}
                """);
        when(approvalRepository.findByTenantIdAndId(1L, 503L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveRequest(1L, 503L, "too high", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出租户管理员可审批的配额上限");

        verify(approvalRepository, never()).markApprovedIfPending(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void approveRequest_shouldRejectQuotaPayloadMissingMonthlyLinkLimit() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of());
        ApprovalRequest pending = quotaRequest("""
                {"type":"applicationQuotaIncrease","version":1,"monthlyClickLimit":1000000}
                """);
        when(approvalRepository.findByTenantIdAndId(1L, 503L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveRequest(1L, 503L, "missing link limit", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("monthlyLinkLimit");

        verify(approvalRepository, never()).markApprovedIfPending(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void approveRequest_shouldRejectLegacyQuotaTextPayload() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of());
        ApprovalRequest pending = quotaRequest("monthlyLinkLimit=50000,monthlyClickLimit=1000000");
        when(approvalRepository.findByTenantIdAndId(1L, 503L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveRequest(1L, 503L, "legacy text", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("配额审批 payload 不合法");

        verify(approvalRepository, never()).markApprovedIfPending(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void approveRequest_shouldPropagateExecutorFailureWithoutAuditOrExecutedTransition() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of(executionPort));
        ApprovalRequest pending = request(ApprovalStatus.PENDING_APPROVAL);
        when(approvalRepository.findByTenantIdAndId(1L, 501L)).thenReturn(Optional.of(pending));
        when(executionPort.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).thenReturn(true);
        when(approvalRepository.markApprovedIfPending(1L, 501L, 8L, "approver@example.com", "ok", NOW))
                .thenReturn(true);
        doThrow(new BusinessException(com.linkforge.contract.api.ErrorCode.BAD_REQUEST, "executor failed"))
                .when(executionPort).execute(any(), eq(NOW));

        assertThatThrownBy(() -> service.approveRequest(1L, 501L, "ok", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("executor failed");

        verify(approvalRepository, never()).markExecutedIfApproved(anyLong(), anyLong(), any());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void approveRequest_shouldFailWhenExecutedTransitionCannotBeRecordedAfterExecutorSuccess() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        GovernanceService service = service(idGenerator, approvalRepository, auditLogRepository, List.of(executionPort));
        ApprovalRequest pending = request(ApprovalStatus.PENDING_APPROVAL);
        when(approvalRepository.findByTenantIdAndId(1L, 501L)).thenReturn(Optional.of(pending));
        when(executionPort.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).thenReturn(true);
        when(approvalRepository.markApprovedIfPending(1L, 501L, 8L, "approver@example.com", "ok", NOW))
                .thenReturn(true);
        when(approvalRepository.markExecutedIfApproved(1L, 501L, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.approveRequest(1L, 501L, "ok", approver(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批执行状态更新失败");

        verify(executionPort).execute(any(ApprovalExecutionRequest.class), eq(NOW));
        verifyNoInteractions(auditLogRepository);
    }

    private static GovernanceService service(
            SnowflakeIdGenerator idGenerator,
            ApprovalRepository approvalRepository,
            AuditLogRepository auditLogRepository,
            List<ApprovalExecutionPort> executionPorts
    ) {
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        return new GovernanceService(idGenerator, approvalRepository, auditLogRepository, clock, executionPorts);
    }

    private static UserActor approver() {
        return new UserActor(1L, 8L, "approver@example.com", Set.of("TENANT_ADMIN"));
    }

    private static ApprovalRequest request(ApprovalStatus status) {
        return new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                status,
                null,
                null,
                null,
                linkDestinationPayload(101L, "https://example.com/old"),
                linkDestinationPayload(101L, "https://example.com/new"),
                NOW.minusHours(1),
                null,
                null
        );
    }

    private static ApprovalRequest decidedRequest(ApprovalStatus status, LocalDateTime decidedAt, LocalDateTime executedAt) {
        return new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                status,
                8L,
                "approver@example.com",
                "ok",
                linkDestinationPayload(101L, "https://example.com/old"),
                linkDestinationPayload(101L, "https://example.com/new"),
                NOW.minusHours(1),
                decidedAt,
                executedAt
        );
    }

    private static ApprovalRequest decisionOnlyRequest(ApprovalStatus status) {
        return new ApprovalRequest(
                502L,
                1L,
                SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                2001L,
                7L,
                "requester@example.com",
                status,
                null,
                null,
                null,
                null,
                analyticsDetailExportPayload(),
                NOW.minusHours(1),
                null,
                null
        );
    }

    private static ApprovalRequest decisionOnlyDecidedRequest(
            ApprovalStatus status,
            LocalDateTime decidedAt,
            LocalDateTime executedAt
    ) {
        return new ApprovalRequest(
                502L,
                1L,
                SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                2001L,
                7L,
                "requester@example.com",
                status,
                8L,
                "approver@example.com",
                "ok",
                null,
                analyticsDetailExportPayload(),
                NOW.minusHours(1),
                decidedAt,
                executedAt
        );
    }

    private static ApprovalRequest quotaRequest(String afterSnapshot) {
        return new ApprovalRequest(
                503L,
                1L,
                SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                2001L,
                7L,
                "requester@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                null,
                afterSnapshot,
                NOW.minusHours(1),
                null,
                null
        );
    }

    private static String linkDestinationPayload(long linkId, String originalUrl) {
        return ApprovalPayloadCodec.write(ApprovalPayloads.LinkDestinationChangePayload.v1(linkId, originalUrl));
    }

    private static String analyticsDetailExportPayload() {
        return ApprovalPayloadCodec.write(ApprovalPayloads.AnalyticsDetailExportPayload.v1(
                101L,
                LocalDateTime.parse("2026-04-01T00:00:00"),
                LocalDateTime.parse("2026-04-02T00:00:00")
        ));
    }
}
