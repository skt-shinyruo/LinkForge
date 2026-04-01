package com.linkforge.contract.governance;

public record ApprovalRequestView(
        long id,
        long tenantId,
        SensitiveOperation operation,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        String status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
