package com.linkforge.governance.application;

import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;

import java.time.LocalDateTime;

/** 审批列表读模型；刻意不包含 before/after 版本化 payload。 */
public record ApprovalRequestSummaryResult(
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
        LocalDateTime createdAt
) {
}
