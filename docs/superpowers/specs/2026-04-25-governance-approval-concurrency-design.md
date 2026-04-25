# Governance Approval Concurrency Hardening Design

**Date:** 2026-04-25

## Problem Statement

The governance approval execution path currently lacks a reliable state-transition boundary.

`GovernanceService.approveRequest` loads an approval request, checks actor validity and approval matrix rules, then calls `executeApprovedRequest` before updating the approval row. It does not require the loaded request to be `PENDING_APPROVAL`.

`ApprovalRequestMapper.updateDecision` updates by `id` only. It does not include `tenant_id` or an expected current status in the `WHERE` clause, and the application-facing repository method returns `void`, so the service cannot detect whether it won a concurrent approval race.

`executeApprovedRequest` silently does nothing when no `ApprovalExecutionPort` supports the operation, but the outer approval flow still marks the request as `EXECUTED`. That makes `EXECUTED` mean "approval endpoint completed" instead of "approved business action was executed".

The result is that double-clicks or concurrent approvals can execute side effects more than once, write duplicate approval audit logs, and overwrite the approver fields. Operations without an executor can also be incorrectly reported as executed.

## Goals

1. An approval request can be approved only while it is `PENDING_APPROVAL`.
2. Only one concurrent caller can claim the right to approve and execute a request.
3. Side effects run only after the approval claim has been atomically recorded.
4. `EXECUTED` means a matching executor completed successfully.
5. Missing executors must not produce a false `EXECUTED` status.
6. Approval audit logs are written exactly once for the successful approval decision.
7. Tenant scoping must be part of all approval state updates.

## Non-Goals

- Do not add reject, cancel, or expire workflows.
- Do not redesign every approval operation or require every `SensitiveOperation` to have an executor.
- Do not change the HTTP approval endpoint shape unless existing error mapping cannot express the new failures.
- Do not change the shortlink executor's internal optimistic-lock behavior; governance should enforce one-time approval independently.

## Current Relevant Code

- `server/governance/application/src/main/java/com/linkforge/governance/application/GovernanceService.java`
  - `approveRequest` reads the request and executes before persisting the decision.
  - `executeApprovedRequest` uses `findFirst().ifPresent(...)`, making missing executors silent.
- `server/governance/application/src/main/java/com/linkforge/governance/application/port/ApprovalRepository.java`
  - `updateDecision(...)` returns `void`.
- `server/governance/infrastructure/src/main/resources/com/linkforge/governance/infrastructure/persistence/mapper/ApprovalRequestMapper.xml`
  - `updateDecision` uses `WHERE id = #{requestId}` only.
- `server/governance/domain/src/main/java/com/linkforge/governance/domain/ApprovalStatus.java`
  - Existing statuses include `PENDING_APPROVAL`, `APPROVED`, and `EXECUTED`.
- `server/shortlink/application/src/main/java/com/linkforge/shortlink/application/approval/LinkDestinationChangeApprovalExecutor.java`
  - Currently the only discovered `ApprovalExecutionPort` implementation.

## Recommended Approach

Use a compare-and-set state transition before any business side effect.

The approval service should first validate the loaded request, resolve whether an executor exists, and atomically move the row from `PENDING_APPROVAL` to `APPROVED`. Only the transaction that wins this conditional update may execute the business action or write the approval audit log.

If an executor exists, run it after the request is marked `APPROVED`, then mark the request `EXECUTED`. If no executor exists, the approval remains `APPROVED` with `executed_at = null`.

This keeps the decision and execution semantics distinct while using the existing `ApprovalStatus` enum.

## Target Status Semantics

### `PENDING_APPROVAL`

The request is waiting for an approver. This is the only status accepted by `approveRequest`.

### `APPROVED`

The approval decision has been accepted and persisted. Approver identity, decision reason, and `decided_at` are recorded.

For operations without an executor, this is the final successful state.

For operations with an executor, this is an intermediate state inside the approving transaction until the executor completes.

### `EXECUTED`

The approval decision was accepted and the matching executor completed successfully. `executed_at` must be set.

### `REJECTED`, `CANCELLED`, `EXPIRED`

These are terminal or non-approvable statuses for this flow. `approveRequest` must reject them before execution and before any audit write.

## Approval Flow

The target flow for `approveRequest` is:

1. Resolve and validate the actor.
2. Load the approval request by `tenantId` and `requestId`.
3. Reject when the request does not exist.
4. Reject when `status != PENDING_APPROVAL`.
5. Reject self-approval.
6. Enforce approval matrix rules.
7. Resolve the matching executor for the operation, if any.
8. Conditionally mark the request `APPROVED` only when the row is still `PENDING_APPROVAL`.
9. If the conditional update affects zero rows, reject as a stale approval decision; do not execute and do not audit.
10. If an executor exists, run it and then conditionally mark the request `EXECUTED`.
11. Append one `APPROVE_REQUEST` audit log.
12. Reload and return the latest approval request.

All steps after the conditional `APPROVED` claim should remain in the same transaction as the approval endpoint call. If executor execution fails, the approval decision and audit insert should roll back with the transaction.

## Repository Contract

Replace or supplement `updateDecision(...)` with explicit state-transition methods that return whether the row changed.

Required behavior:

```java
boolean markApprovedIfPending(
        long tenantId,
        long requestId,
        long approverUserId,
        String approverEmail,
        String decisionReason,
        LocalDateTime decidedAt
);

boolean markExecutedIfApproved(
        long tenantId,
        long requestId,
        LocalDateTime executedAt
);
```

`markApprovedIfPending` must set:

- `status = 'APPROVED'`
- `approver_user_id`
- `approver_email`
- `decision_reason`
- `decided_at`
- `executed_at = NULL`

and must update only:

```sql
WHERE tenant_id = ?
  AND id = ?
  AND status = 'PENDING_APPROVAL'
```

`markExecutedIfApproved` must set:

- `status = 'EXECUTED'`
- `executed_at`

and must update only:

```sql
WHERE tenant_id = ?
  AND id = ?
  AND status = 'APPROVED'
```

The service must treat a `false` result as a concurrent state-change failure and stop without writing an approval audit log.

## Executor Resolution

`executeApprovedRequest` should no longer silently ignore missing executors.

Split executor resolution from execution:

- `findExecutor(SensitiveOperation operation)` returns an optional executor.
- Operations with an executor must execute before becoming `EXECUTED`.
- Operations without an executor remain `APPROVED`.

This avoids falsely reporting unsupported operations as executed while still allowing approvals that are decision-only today.

## Error Handling

Non-pending approvals should fail with a business error that clearly tells callers the request state has changed and they should refresh.

A failed conditional update should use the same stale-state business error because it means another transaction changed the approval between read and claim.

An executor failure should propagate as its existing domain/business error. The surrounding transaction should roll back, leaving the request in its previous `PENDING_APPROVAL` state.

If `markExecutedIfApproved` returns `false` after a successful executor call, treat it as an internal consistency failure. This should be extremely rare inside one transaction; the important requirement is that the service must not silently return an incorrect status.

## Audit Requirements

`SUBMIT_REQUEST` behavior stays unchanged.

`APPROVE_REQUEST` must be inserted only after the caller wins the `PENDING_APPROVAL -> APPROVED` transition.

Duplicate or stale approval attempts must not write `APPROVE_REQUEST`.

For operations with an executor, the audit log may be written after execution succeeds so an approved-and-executed request has one success audit. For operations without an executor, the audit log records the approval decision ending in `APPROVED`.

## Testing Strategy

### Unit Tests

Add or update `GovernanceServiceTest` cases for:

1. A non-`PENDING_APPROVAL` request is rejected before executor execution and before audit insert.
2. A stale claim where `markApprovedIfPending` returns `false` is rejected before executor execution and before audit insert.
3. A request with a matching executor transitions through `APPROVED`, executes once, then transitions to `EXECUTED`.
4. A request without a matching executor transitions to `APPROVED` and returns `APPROVED` with no `executedAt`.
5. Executor failure propagates and prevents approval audit insertion.
6. `markExecutedIfApproved` returning `false` is treated as an internal consistency failure.

### Persistence Tests

Add focused mapper or repository tests proving:

1. `markApprovedIfPending` updates one row only for the same tenant and `PENDING_APPROVAL` status.
2. `markApprovedIfPending` updates zero rows for `EXECUTED`, `APPROVED`, `REJECTED`, `CANCELLED`, or `EXPIRED`.
3. `markExecutedIfApproved` updates one row only from `APPROVED`.
4. `markExecutedIfApproved` updates zero rows for a different tenant.

### Integration Test

Add a concurrent approval integration test using two callers against the same request:

1. Create a `PENDING_APPROVAL` request.
2. Start two approval calls at the same time.
3. Assert exactly one call succeeds.
4. Assert the final approval row has a single approver and expected terminal status.
5. Assert exactly one `APPROVE_REQUEST` audit log exists for the request.
6. For an operation with an executor, assert the executor-side business change happens once.

## Acceptance Criteria

- `approveRequest` never approves an already approved, executed, rejected, cancelled, or expired request.
- Concurrent approvals cannot both execute side effects.
- Concurrent approvals cannot overwrite the winning approver fields.
- Duplicate approval attempts do not create duplicate `APPROVE_REQUEST` audit logs.
- Missing executors no longer produce `EXECUTED`.
- SQL state updates include both tenant scope and expected current status.
- Focused governance application tests and persistence/integration tests cover the stale-state path.

## Open Decisions Resolved

This design uses existing statuses instead of introducing `EXECUTING`. The implementation can keep `APPROVED` as a short-lived in-transaction state for operations with executors, because the approval transaction should roll back on executor failure.

This design treats operations without executors as decision-only approvals ending in `APPROVED`. That is safer than marking them `EXECUTED` and avoids blocking current operations that intentionally do not have an executor yet.
