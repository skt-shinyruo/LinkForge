package com.linkforge.governance.application;

import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalExecutionPort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceServiceTest {

    @Test
    void approveRequest_shouldExecuteSupportedApprovalBeforeMarkingExecuted() {
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        ApprovalRepository approvalRepository = mock(ApprovalRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        ApprovalExecutionPort executionPort = mock(ApprovalExecutionPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        GovernanceService service = new GovernanceService(
                idGenerator,
                approvalRepository,
                auditLogRepository,
                clock,
                List.of(executionPort)
        );
        LocalDateTime now = LocalDateTime.parse("2026-04-01T12:00:00");
        ApprovalRequest pending = new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new",
                now.minusHours(1),
                null,
                null
        );
        ApprovalRequest executed = new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                ApprovalStatus.EXECUTED,
                8L,
                "approver@example.com",
                "ok",
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new",
                now.minusHours(1),
                now,
                now
        );
        when(approvalRepository.findByTenantIdAndId(1L, 501L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(executed));
        when(executionPort.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).thenReturn(true);
        UserActor approver = new UserActor(1L, 8L, "approver@example.com", Set.of("TENANT_ADMIN"));

        GovernanceService.ApprovalRequestDto actual =
                service.approveRequest(1L, 501L, "ok", approver, now);

        assertThat(actual.status()).isEqualTo(ApprovalStatus.EXECUTED);
        InOrder inOrder = inOrder(executionPort, approvalRepository, auditLogRepository);
        inOrder.verify(executionPort).execute(
                argThat(request -> request.equals(new ApprovalExecutionRequest(
                        501L,
                        1L,
                        SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                        2001L,
                        "linkId=101\noriginalUrl=https://example.com/old",
                        "linkId=101\noriginalUrl=https://example.com/new"
                ))),
                eq(now)
        );
        inOrder.verify(approvalRepository).updateDecision(
                501L,
                ApprovalStatus.EXECUTED.name(),
                8L,
                "approver@example.com",
                "ok",
                now,
                now
        );
        inOrder.verify(auditLogRepository).insert(any(AuditLog.class));
    }
}
