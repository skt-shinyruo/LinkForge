# Governance Approval Concurrency Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make governance approval execution single-winner, status-aware, tenant-scoped, and semantically honest about `APPROVED` versus `EXECUTED`.

**Architecture:** The application service will claim approval decisions with a compare-and-set transition from `PENDING_APPROVAL` to `APPROVED` before any side effect runs. Executor-backed operations will then transition from `APPROVED` to `EXECUTED`; operations without an executor will remain `APPROVED`. MyBatis updates will enforce tenant scope and expected current status.

**Tech Stack:** Java 17, Spring Boot, Spring transactions, MyBatis XML mappers, JUnit 5, AssertJ, Mockito, Maven, Testcontainers MySQL/Redis integration tests.

---

## File Structure

- Modify `server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java`
  - Replace the unguarded `updateDecision(...)` command with explicit boolean-returning state transitions.
- Modify `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
  - Enforce `PENDING_APPROVAL`, claim `APPROVED`, run executor when present, mark `EXECUTED`, and write audit only after a successful claim.
- Modify `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java`
  - Replace the old "execute before marking" test with behavior tests for state guard, stale claim, executor path, decision-only path, executor failure, and execution-status failure.
- Modify `server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.java`
  - Add MyBatis mapper methods for conditional `APPROVED` and `EXECUTED` transitions.
- Modify `server/governance/infrastructure/src/main/resources/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.xml`
  - Replace unguarded `updateDecision` SQL with tenant-scoped status-guarded updates.
- Modify `server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/ApprovalRepositoryMybatisAdapter.java`
  - Implement the new repository methods by returning `mapper.update(...) > 0`.
- Create `server/integration-tests/src/test/java/com/linkforge/governance/ApprovalStateTransitionPersistenceIntegrationTest.java`
  - Verify the SQL-level state guards against MySQL.
- Modify `server/integration-tests/src/test/java/com/linkforge/governance/ApprovalWorkflowIntegrationTest.java`
  - Update decision-only approval expectations to `APPROVED` and add duplicate approval coverage.
- Modify `server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java`
  - Update analytics export approval expectation from `EXECUTED` to `APPROVED`.
- Modify `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ApplicationAwareShortLinkIntegrationTest.java`
  - Add executor-backed concurrent approval coverage for shortlink destination changes.

---

### Task 1: Application Service State Machine

**Files:**
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java`
- Modify: `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
- Modify: `server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java`

- [ ] **Step 1: Write failing GovernanceService tests**

Replace `GovernanceServiceTest` with focused tests for the new behavior. Keep the package and imports aligned with the file below:

```java
package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
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

        GovernanceService.ApprovalRequestDto actual =
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
                        "linkId=101\noriginalUrl=https://example.com/old",
                        "linkId=101\noriginalUrl=https://example.com/new"
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

        GovernanceService.ApprovalRequestDto actual =
                service.approveRequest(1L, 502L, "ok", approver(), NOW);

        assertThat(actual.status()).isEqualTo(ApprovalStatus.APPROVED);
        verify(approvalRepository).markApprovedIfPending(1L, 502L, 8L, "approver@example.com", "ok", NOW);
        verify(approvalRepository, never()).markExecutedIfApproved(anyLong(), anyLong(), any());
        verify(auditLogRepository).insert(any(AuditLog.class));
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
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new",
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
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new",
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
                "linkId=101,from=2026-04-01T00:00,to=2026-04-02T00:00",
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
                "linkId=101,from=2026-04-01T00:00,to=2026-04-02T00:00",
                NOW.minusHours(1),
                decidedAt,
                executedAt
        );
    }
}
```

- [ ] **Step 2: Run application tests and verify the intended failure**

Run:

```bash
cd server && mvn -q -pl governance/application -Dtest=GovernanceServiceTest test
```

Expected: FAIL at compilation because `ApprovalRepository` does not yet define `markApprovedIfPending` and `markExecutedIfApproved`, and `GovernanceService` still calls `updateDecision`.

- [ ] **Step 3: Update the repository port**

Replace `updateDecision(...)` in `ApprovalRepository` with:

```java
boolean markApprovedIfPending(
        long tenantId,
        long requestId,
        long approverUserId,
        String approverEmail,
        String decisionReason,
        java.time.LocalDateTime decidedAt
);

boolean markExecutedIfApproved(
        long tenantId,
        long requestId,
        java.time.LocalDateTime executedAt
);
```

- [ ] **Step 4: Implement the new application flow**

In `GovernanceService`, add `java.util.Optional` to imports.

Replace `approveRequest` and `executeApprovedRequest` with the following methods, and add the helper methods shown below:

```java
@Transactional
public ApprovalRequestDto approveRequest(long tenantId, long requestId, String reason, UserActor actor, LocalDateTime requestedAt) {
    UserActor effectiveActor = requireActor(tenantId, actor);
    ApprovalRequest request = approvalRepository.findByTenantIdAndId(tenantId, requestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审批请求不存在"));
    if (request.status() != ApprovalStatus.PENDING_APPROVAL) {
        throw approvalStateChanged();
    }
    if (request.requestedByUserId() == effectiveActor.userId()) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "申请人与审批人不能是同一人");
    }
    enforceApprovalMatrix(effectiveActor, request);
    LocalDateTime now = requestedAt == null ? LocalDateTime.now(clock) : requestedAt;
    SensitiveOperation operation = toContractOperation(request.operationType());
    Optional<ApprovalExecutionPort> executor = findExecutor(operation);

    boolean claimed = approvalRepository.markApprovedIfPending(
            request.tenantId(),
            request.id(),
            effectiveActor.userId(),
            effectiveActor.email(),
            reason,
            now
    );
    if (!claimed) {
        throw approvalStateChanged();
    }

    if (executor.isPresent()) {
        executeApprovedRequest(request, operation, executor.get(), now);
        if (!approvalRepository.markExecutedIfApproved(request.tenantId(), request.id(), now)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "审批执行状态更新失败");
        }
    }

    appendAuditLog(tenantId, effectiveActor, "APPROVE_REQUEST", "approval_request", String.valueOf(requestId), requestId, request.beforeSnapshot(), request.afterSnapshot(), now);
    return approvalRepository.findByTenantIdAndId(tenantId, requestId)
            .map(this::toDto)
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "审批请求更新失败"));
}

private void executeApprovedRequest(
        ApprovalRequest request,
        SensitiveOperation operation,
        ApprovalExecutionPort executor,
        LocalDateTime executedAt
) {
    ApprovalExecutionRequest executionRequest = new ApprovalExecutionRequest(
            request.id(),
            request.tenantId(),
            operation,
            request.targetApplicationId(),
            request.beforeSnapshot(),
            request.afterSnapshot()
    );
    executor.execute(executionRequest, executedAt);
}

private Optional<ApprovalExecutionPort> findExecutor(SensitiveOperation operation) {
    return approvalExecutionPorts.stream()
            .filter(port -> port.supports(operation))
            .findFirst();
}

private static BusinessException approvalStateChanged() {
    return new BusinessException(ErrorCode.BAD_REQUEST, "审批请求状态已变化，请刷新后重试");
}
```

- [ ] **Step 5: Run application tests and verify they pass**

Run:

```bash
cd server && mvn -q -pl governance/application -Dtest=GovernanceServiceTest test
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java \
        server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java \
        server/governance/application/src/test/java/com/linkforge/governance/application/GovernanceServiceTest.java
git commit -m "fix(governance): claim approvals before execution"
```

---

### Task 2: Tenant-Scoped Conditional Persistence

**Files:**
- Modify: `server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.java`
- Modify: `server/governance/infrastructure/src/main/resources/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.xml`
- Modify: `server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/ApprovalRepositoryMybatisAdapter.java`
- Create: `server/integration-tests/src/test/java/com/linkforge/governance/ApprovalStateTransitionPersistenceIntegrationTest.java`

- [ ] **Step 1: Add failing persistence integration tests**

Create `ApprovalStateTransitionPersistenceIntegrationTest`:

```java
package com.linkforge.governance;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.governance.application.port.ApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ApprovalStateTransitionPersistenceIntegrationTest extends GovernancePersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 82101L;
    private static final long OTHER_TENANT_ID = 82102L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-04-01T12:00:00");

    @Autowired
    ApprovalRepository approvalRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpTenantsAndCleanRows() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM approval_requests WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
    }

    @Test
    void markApprovedIfPending_shouldUpdateOnlyPendingRowForSameTenant() {
        insertApproval(9101L, TENANT_ID, "PENDING_APPROVAL");
        insertApproval(9102L, OTHER_TENANT_ID, "PENDING_APPROVAL");

        boolean updated = approvalRepository.markApprovedIfPending(
                TENANT_ID,
                9101L,
                8L,
                "approver@example.com",
                "ok",
                NOW
        );
        boolean wrongTenantUpdated = approvalRepository.markApprovedIfPending(
                TENANT_ID,
                9102L,
                8L,
                "approver@example.com",
                "ok",
                NOW
        );

        assertThat(updated).isTrue();
        assertThat(wrongTenantUpdated).isFalse();
        assertThat(status(9101L)).isEqualTo("APPROVED");
        assertThat(approverUserId(9101L)).isEqualTo(8L);
        assertThat(executedAt(9101L)).isNull();
        assertThat(status(9102L)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void markApprovedIfPending_shouldNotUpdateNonPendingRows() {
        insertApproval(9201L, TENANT_ID, "APPROVED");
        insertApproval(9202L, TENANT_ID, "EXECUTED");
        insertApproval(9203L, TENANT_ID, "REJECTED");
        insertApproval(9204L, TENANT_ID, "CANCELLED");
        insertApproval(9205L, TENANT_ID, "EXPIRED");

        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9201L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9202L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9203L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9204L, 8L, "approver@example.com", "ok", NOW)).isFalse();
        assertThat(approvalRepository.markApprovedIfPending(TENANT_ID, 9205L, 8L, "approver@example.com", "ok", NOW)).isFalse();

        assertThat(status(9201L)).isEqualTo("APPROVED");
        assertThat(status(9202L)).isEqualTo("EXECUTED");
        assertThat(status(9203L)).isEqualTo("REJECTED");
        assertThat(status(9204L)).isEqualTo("CANCELLED");
        assertThat(status(9205L)).isEqualTo("EXPIRED");
    }

    @Test
    void markExecutedIfApproved_shouldUpdateOnlyApprovedRowForSameTenant() {
        insertApproval(9301L, TENANT_ID, "APPROVED");
        insertApproval(9302L, TENANT_ID, "PENDING_APPROVAL");
        insertApproval(9303L, OTHER_TENANT_ID, "APPROVED");

        boolean updated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9301L, NOW);
        boolean pendingUpdated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9302L, NOW);
        boolean wrongTenantUpdated = approvalRepository.markExecutedIfApproved(TENANT_ID, 9303L, NOW);

        assertThat(updated).isTrue();
        assertThat(pendingUpdated).isFalse();
        assertThat(wrongTenantUpdated).isFalse();
        assertThat(status(9301L)).isEqualTo("EXECUTED");
        assertThat(executedAt(9301L)).isEqualTo(NOW);
        assertThat(status(9302L)).isEqualTo("PENDING_APPROVAL");
        assertThat(status(9303L)).isEqualTo("APPROVED");
    }

    private void insertApproval(long requestId, long tenantId, String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO approval_requests (
                            id,
                            tenant_id,
                            operation_type,
                            target_application_id,
                            requested_by_user_id,
                            requested_by_email,
                            status,
                            before_snapshot,
                            after_snapshot,
                            created_at
                        ) VALUES (?, ?, 'ANALYTICS_DETAIL_EXPORT', 2001, 7, 'requester@example.com', ?, NULL, 'snapshot', ?)
                        """,
                requestId,
                tenantId,
                status,
                NOW.minusHours(1)
        );
    }

    private String status(long requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM approval_requests WHERE id = ?", String.class, requestId);
    }

    private Long approverUserId(long requestId) {
        return jdbcTemplate.queryForObject("SELECT approver_user_id FROM approval_requests WHERE id = ?", Long.class, requestId);
    }

    private LocalDateTime executedAt(long requestId) {
        return jdbcTemplate.queryForObject("SELECT executed_at FROM approval_requests WHERE id = ?", LocalDateTime.class, requestId);
    }
}
```

- [ ] **Step 2: Run the persistence test and verify the intended failure**

Run:

```bash
cd server && mvn -q -pl integration-tests -Dtest=ApprovalStateTransitionPersistenceIntegrationTest test
```

Expected: FAIL at compilation because infrastructure classes do not yet implement the new repository contract.

- [ ] **Step 3: Update the mapper interface**

Replace `updateDecision(...)` in `ApprovalRequestMapper` with:

```java
int markApprovedIfPending(
        @Param("tenantId") long tenantId,
        @Param("requestId") long requestId,
        @Param("approverUserId") long approverUserId,
        @Param("approverEmail") String approverEmail,
        @Param("decisionReason") String decisionReason,
        @Param("decidedAt") LocalDateTime decidedAt
);

int markExecutedIfApproved(
        @Param("tenantId") long tenantId,
        @Param("requestId") long requestId,
        @Param("executedAt") LocalDateTime executedAt
);
```

- [ ] **Step 4: Replace mapper XML updates**

Replace the `<update id="updateDecision">` block in `ApprovalRequestMapper.xml` with:

```xml
<update id="markApprovedIfPending">
    UPDATE approval_requests
    SET status = 'APPROVED',
        approver_user_id = #{approverUserId},
        approver_email = #{approverEmail},
        decision_reason = #{decisionReason},
        decided_at = #{decidedAt},
        executed_at = NULL
    WHERE tenant_id = #{tenantId}
      AND id = #{requestId}
      AND status = 'PENDING_APPROVAL'
</update>

<update id="markExecutedIfApproved">
    UPDATE approval_requests
    SET status = 'EXECUTED',
        executed_at = #{executedAt}
    WHERE tenant_id = #{tenantId}
      AND id = #{requestId}
      AND status = 'APPROVED'
</update>
```

- [ ] **Step 5: Update the MyBatis repository adapter**

Replace `updateDecision(...)` in `ApprovalRepositoryMybatisAdapter` with:

```java
@Override
public boolean markApprovedIfPending(
        long tenantId,
        long requestId,
        long approverUserId,
        String approverEmail,
        String decisionReason,
        LocalDateTime decidedAt
) {
    return mapper.markApprovedIfPending(
            tenantId,
            requestId,
            approverUserId,
            approverEmail,
            decisionReason,
            decidedAt
    ) > 0;
}

@Override
public boolean markExecutedIfApproved(long tenantId, long requestId, LocalDateTime executedAt) {
    return mapper.markExecutedIfApproved(tenantId, requestId, executedAt) > 0;
}
```

- [ ] **Step 6: Run persistence tests and verify they pass**

Run:

```bash
cd server && mvn -q -pl integration-tests -Dtest=ApprovalStateTransitionPersistenceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Run the full governance module tests**

Run:

```bash
cd server && mvn -q -pl governance -am test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.java \
        server/governance/infrastructure/src/main/resources/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.xml \
        server/governance/infrastructure/src/main/java/com/linkforge/governance/infrastructure/persistence/ApprovalRepositoryMybatisAdapter.java \
        server/integration-tests/src/test/java/com/linkforge/governance/ApprovalStateTransitionPersistenceIntegrationTest.java
git commit -m "fix(governance): guard approval state transitions in persistence"
```

---

### Task 3: Decision-Only Approval Workflow Semantics

**Files:**
- Modify: `server/integration-tests/src/test/java/com/linkforge/governance/ApprovalWorkflowIntegrationTest.java`
- Modify: `server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java`

- [ ] **Step 1: Update decision-only expectations and add duplicate approval coverage**

In `ApprovalWorkflowIntegrationTest`:

1. Add `import com.linkforge.governance.domain.ApprovalStatus;`.
2. Change quota approval assertion from `ApprovalStatus.EXECUTED` to `ApprovalStatus.APPROVED`.
3. Change external-domain approval assertion from `ApprovalStatus.EXECUTED` to `ApprovalStatus.APPROVED`.
4. Add this test method:

```java
@Test
void approved_request_should_not_be_approved_again_or_write_duplicate_audit() {
    UserActor requester = new UserActor(TENANT_ID, 207L, "requester-duplicate@example.com", Set.of("TENANT_ADMIN"));
    authenticateAsTenantAdmin(TENANT_ID, 207L, "requester-duplicate@example.com");
    GovernanceService.ApprovalRequestDto request = governanceService.submitRequest(
            TENANT_ID,
            new GovernanceService.SubmitApprovalRequest(
                    SensitiveOperationType.APPLICATION_QUOTA_INCREASE,
                    9004L,
                    null,
                    "monthlyLinkLimit=50000,monthlyClickLimit=1000000",
                    requester,
                    LocalDateTime.now(ZoneOffset.UTC)
            )
    );

    UserActor firstApprover = new UserActor(TENANT_ID, 208L, "first-approver@example.com", Set.of("TENANT_ADMIN"));
    GovernanceService.ApprovalRequestDto approved = governanceService.approveRequest(
            TENANT_ID,
            request.id(),
            "first",
            firstApprover,
            LocalDateTime.now(ZoneOffset.UTC)
    );

    UserActor secondApprover = new UserActor(TENANT_ID, 209L, "second-approver@example.com", Set.of("TENANT_ADMIN"));
    assertThatThrownBy(() -> governanceService.approveRequest(
            TENANT_ID,
            request.id(),
            "second",
            secondApprover,
            LocalDateTime.now(ZoneOffset.UTC)
    ))
            .hasMessageContaining("审批请求状态已变化");

    assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
    Long approveAuditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE request_id = ? AND action_type = 'APPROVE_REQUEST'",
            Long.class,
            request.id()
    );
    Long winningApprover = jdbcTemplate.queryForObject(
            "SELECT approver_user_id FROM approval_requests WHERE id = ?",
            Long.class,
            request.id()
    );
    assertThat(approveAuditCount).isEqualTo(1L);
    assertThat(winningApprover).isEqualTo(208L);
}
```

- [ ] **Step 2: Update the control-plane end-to-end expectation**

In `ControlPlaneEndToEndIntegrationTest`, change:

```java
assertThat(approved.get("data").get("status").asText()).isEqualTo("EXECUTED");
```

to:

```java
assertThat(approved.get("data").get("status").asText()).isEqualTo("APPROVED");
```

This approval is for `ANALYTICS_DETAIL_EXPORT`, which currently has no executor and should be decision-only.

- [ ] **Step 3: Run workflow tests and verify they pass**

Run:

```bash
cd server && mvn -q -pl integration-tests -Dtest=ApprovalWorkflowIntegrationTest,ControlPlaneEndToEndIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Commit Task 3**

```bash
git add server/integration-tests/src/test/java/com/linkforge/governance/ApprovalWorkflowIntegrationTest.java \
        server/integration-tests/src/test/java/com/linkforge/platform/ControlPlaneEndToEndIntegrationTest.java
git commit -m "test(governance): cover decision-only approval semantics"
```

---

### Task 4: Executor-Backed Concurrent Approval Integration

**Files:**
- Modify: `server/integration-tests/src/test/java/com/linkforge/shortlink/application/ApplicationAwareShortLinkIntegrationTest.java`

- [ ] **Step 1: Add concurrency imports**

Add these imports to `ApplicationAwareShortLinkIntegrationTest`:

```java
import com.linkforge.contract.api.BusinessException;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
```

- [ ] **Step 2: Add a concurrent approval test for shortlink destination changes**

Add this test method to `ApplicationAwareShortLinkIntegrationTest`:

```java
@Test
void concurrent_public_link_destination_approval_should_execute_once_and_audit_once() throws Exception {
    ShortLinkService.LinkDto created = shortLinkService.create(
            TENANT_ID,
            ShortLinkService.CreatedBy.user(USER_ID),
            new ShortLinkService.CreateLinkRequest(
                    "https://example.com/concurrent-original",
                    "note",
                    null,
                    null,
                    null,
                    Set.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    applicationId,
                    authorizedDomainId,
                    "ACTIVE"
            )
    );

    shortLinkService.update(
            TENANT_ID,
            created.id(),
            new ShortLinkService.UpdateLinkRequest(
                    "https://example.com/concurrent-approved",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ),
            tenantAdminActor(),
            LocalDateTime.now(ZoneOffset.UTC)
    );

    Long approvalId = jdbcTemplate.queryForObject(
            """
                    SELECT id
                    FROM approval_requests
                    WHERE operation_type = 'PUBLIC_LINK_DESTINATION_CHANGE'
                      AND target_application_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """,
            Long.class,
            applicationId
    );
    Long versionBeforeApproval = jdbcTemplate.queryForObject(
            "SELECT version FROM short_links WHERE id = ?",
            Long.class,
            created.id()
    );

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
        Future<ApprovalAttempt> first = submitApprovalAttempt(
                executor,
                start,
                () -> governanceService.approveRequest(
                        TENANT_ID,
                        approvalId,
                        "first",
                        new UserActor(TENANT_ID, USER_ID + 1, "first-approver@example.com", Set.of("TENANT_ADMIN")),
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );
        Future<ApprovalAttempt> second = submitApprovalAttempt(
                executor,
                start,
                () -> governanceService.approveRequest(
                        TENANT_ID,
                        approvalId,
                        "second",
                        new UserActor(TENANT_ID, USER_ID + 2, "second-approver@example.com", Set.of("TENANT_ADMIN")),
                        LocalDateTime.now(ZoneOffset.UTC)
                )
        );

        start.countDown();

        ApprovalAttempt firstResult = first.get(10, TimeUnit.SECONDS);
        ApprovalAttempt secondResult = second.get(10, TimeUnit.SECONDS);

        assertThat(firstResult.success() ^ secondResult.success()).isTrue();
        assertThat(firstResult.success() ? firstResult.status() : secondResult.status()).isEqualTo(ApprovalStatus.EXECUTED);
        assertThat(firstResult.success() ? secondResult.errorMessage() : firstResult.errorMessage())
                .contains("审批请求状态已变化");
    } finally {
        executor.shutdownNow();
    }

    String originalUrl = jdbcTemplate.queryForObject(
            "SELECT original_url FROM short_links WHERE id = ?",
            String.class,
            created.id()
    );
    Long versionAfterApproval = jdbcTemplate.queryForObject(
            "SELECT version FROM short_links WHERE id = ?",
            Long.class,
            created.id()
    );
    Long approveAuditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE request_id = ? AND action_type = 'APPROVE_REQUEST'",
            Long.class,
            approvalId
    );

    assertThat(originalUrl).isEqualTo("https://example.com/concurrent-approved");
    assertThat(versionAfterApproval).isEqualTo(versionBeforeApproval + 1);
    assertThat(approveAuditCount).isEqualTo(1L);
}
```

- [ ] **Step 3: Add concurrency helpers**

Add these helper types and methods near the bottom of `ApplicationAwareShortLinkIntegrationTest`, before `tenantAdminActor()`:

```java
private static Future<ApprovalAttempt> submitApprovalAttempt(
        ExecutorService executor,
        CountDownLatch start,
        Callable<GovernanceService.ApprovalRequestDto> task
) {
    return executor.submit(() -> {
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            GovernanceService.ApprovalRequestDto dto = task.call();
            return new ApprovalAttempt(true, dto.status(), null);
        } catch (BusinessException ex) {
            return new ApprovalAttempt(false, null, ex.getMessage());
        }
    });
}

private record ApprovalAttempt(boolean success, ApprovalStatus status, String errorMessage) {
}
```

- [ ] **Step 4: Run executor-backed integration tests and verify they pass**

Run:

```bash
cd server && mvn -q -pl integration-tests -Dtest=ApplicationAwareShortLinkIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```bash
git add server/integration-tests/src/test/java/com/linkforge/shortlink/application/ApplicationAwareShortLinkIntegrationTest.java
git commit -m "test(governance): cover concurrent executor-backed approval"
```

---

### Task 5: Full Verification And Cleanup

**Files:**
- Review: all files modified in Tasks 1-4

- [ ] **Step 1: Search for old unguarded update usage**

Run:

```bash
rg -n "updateDecision|markApprovedIfPending|markExecutedIfApproved|WHERE id = #\\{requestId\\}" server/governance server/integration-tests
```

Expected:

- No `updateDecision` references remain.
- `markApprovedIfPending` is defined in the port, mapper, XML, adapter, and tests.
- `markExecutedIfApproved` is defined in the port, mapper, XML, adapter, and tests.
- No approval update SQL remains with only `WHERE id = #{requestId}`.

- [ ] **Step 2: Run focused Maven verification**

Run:

```bash
cd server && mvn -q -pl governance,shortlink/application,integration-tests -am \
  -Dtest=GovernanceServiceTest,ApprovalStateTransitionPersistenceIntegrationTest,ApprovalWorkflowIntegrationTest,ApplicationAwareShortLinkIntegrationTest,ControlPlaneEndToEndIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 3: Run broader server tests**

Run:

```bash
cd server && mvn -q -pl app,integration-tests -am test
```

Expected: PASS.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git diff --stat HEAD~4..HEAD
git status --short
```

Expected:

- Diff includes only governance approval state machine, mapper, and tests described in this plan.
- Working tree is clean.

- [ ] **Step 5: Commit any verification-only cleanup**

Only run this if Step 4 reveals small cleanup edits after the Task 4 commit:

```bash
git add server/governance server/integration-tests
git commit -m "chore(governance): finalize approval concurrency hardening"
```

Expected: either a small cleanup commit is created, or there is nothing to commit because prior task commits already captured all changes.

---

## Notes For Implementers

- Keep `APPROVED` as the final status for operations without an executor. Do not add an executor just to preserve old `EXECUTED` assertions.
- Keep executor failure in the same transaction. Do not catch executor exceptions in `GovernanceService`.
- Do not write `APPROVE_REQUEST` before the `PENDING_APPROVAL -> APPROVED` claim succeeds.
- Do not update approval rows without `tenant_id` and an expected current status in the SQL `WHERE` clause.
- Do not expand `ApprovalRequestDto` just to expose `executedAt`; verify `executed_at` through persistence tests where needed.
