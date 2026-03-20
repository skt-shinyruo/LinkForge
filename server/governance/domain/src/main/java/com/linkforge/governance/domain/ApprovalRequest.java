package com.linkforge.governance.domain;

import java.time.LocalDateTime;

public record ApprovalRequest(
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
}
