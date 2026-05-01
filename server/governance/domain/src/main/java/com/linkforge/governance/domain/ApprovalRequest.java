package com.linkforge.governance.domain;

import java.time.LocalDateTime;

public class ApprovalRequest {

    private final long id;
    private final long tenantId;
    private final SensitiveOperationType operationType;
    private final Long targetApplicationId;
    private final long requestedByUserId;
    private final String requestedByEmail;
    private final ApprovalStatus status;
    private final Long approverUserId;
    private final String approverEmail;
    private final String decisionReason;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final LocalDateTime createdAt;
    private final LocalDateTime decidedAt;
    private final LocalDateTime executedAt;

    public ApprovalRequest(
            long id,
            long tenantId,
            SensitiveOperationType operationType,
            Long targetApplicationId,
            long requestedByUserId,
            String requestedByEmail,
            ApprovalStatus status,
            Long approverUserId,
            String approverEmail,
            String decisionReason,
            String beforeSnapshot,
            String afterSnapshot,
            LocalDateTime createdAt,
            LocalDateTime decidedAt,
            LocalDateTime executedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.operationType = operationType;
        this.targetApplicationId = targetApplicationId;
        this.requestedByUserId = requestedByUserId;
        this.requestedByEmail = requestedByEmail;
        this.status = status;
        this.approverUserId = approverUserId;
        this.approverEmail = approverEmail;
        this.decisionReason = decisionReason;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.createdAt = createdAt;
        this.decidedAt = decidedAt;
        this.executedAt = executedAt;
    }

    public ApprovalRequest approve(
            long approverUserId,
            String approverEmail,
            String decisionReason,
            LocalDateTime decidedAt
    ) {
        if (status != ApprovalStatus.PENDING_APPROVAL) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.APPROVAL_NOT_PENDING);
        }
        if (requestedByUserId == approverUserId) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.SELF_APPROVAL);
        }
        return new ApprovalRequest(
                id,
                tenantId,
                operationType,
                targetApplicationId,
                requestedByUserId,
                requestedByEmail,
                ApprovalStatus.APPROVED,
                approverUserId,
                approverEmail,
                ApprovalDecisionReason.of(decisionReason).value(),
                beforeSnapshot,
                afterSnapshot,
                createdAt,
                decidedAt,
                null
        );
    }

    public ApprovalRequest markExecuted(LocalDateTime executedAt) {
        if (status != ApprovalStatus.APPROVED) {
            throw new ApprovalDomainException(ApprovalDomainException.Reason.APPROVAL_NOT_APPROVED);
        }
        return new ApprovalRequest(
                id,
                tenantId,
                operationType,
                targetApplicationId,
                requestedByUserId,
                requestedByEmail,
                ApprovalStatus.EXECUTED,
                approverUserId,
                approverEmail,
                decisionReason,
                beforeSnapshot,
                afterSnapshot,
                createdAt,
                decidedAt,
                executedAt
        );
    }

    public long id() {
        return id;
    }

    public long tenantId() {
        return tenantId;
    }

    public SensitiveOperationType operationType() {
        return operationType;
    }

    public Long targetApplicationId() {
        return targetApplicationId;
    }

    public long requestedByUserId() {
        return requestedByUserId;
    }

    public String requestedByEmail() {
        return requestedByEmail;
    }

    public ApprovalStatus status() {
        return status;
    }

    public Long approverUserId() {
        return approverUserId;
    }

    public String approverEmail() {
        return approverEmail;
    }

    public String decisionReason() {
        return decisionReason;
    }

    public String beforeSnapshot() {
        return beforeSnapshot;
    }

    public String afterSnapshot() {
        return afterSnapshot;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime decidedAt() {
        return decidedAt;
    }

    public LocalDateTime executedAt() {
        return executedAt;
    }
}
