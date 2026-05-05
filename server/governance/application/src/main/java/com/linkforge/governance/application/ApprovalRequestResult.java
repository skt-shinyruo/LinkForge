package com.linkforge.governance.application;

import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;

public record ApprovalRequestResult(
        long id,
        long tenantId,
        SensitiveOperationType operationType,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        ApprovalStatus status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
