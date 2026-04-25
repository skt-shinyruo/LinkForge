package com.linkforge.contract.governance;

public record ApprovalExecutionRequest(
        long id,
        long tenantId,
        SensitiveOperation operation,
        Long targetApplicationId,
        String beforeSnapshot,
        String afterSnapshot
) {
}
