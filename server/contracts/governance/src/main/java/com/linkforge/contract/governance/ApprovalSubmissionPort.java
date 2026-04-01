package com.linkforge.contract.governance;

public interface ApprovalSubmissionPort {

    ApprovalRequestView submitRequest(
            long tenantId,
            SensitiveOperation operation,
            Long targetApplicationId,
            String beforeSnapshot,
            String afterSnapshot
    );
}
