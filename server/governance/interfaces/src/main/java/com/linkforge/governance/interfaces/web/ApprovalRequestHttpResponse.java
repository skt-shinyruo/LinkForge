package com.linkforge.governance.interfaces.web;

public record ApprovalRequestHttpResponse(
        long id,
        long tenantId,
        String operationType,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        String status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
