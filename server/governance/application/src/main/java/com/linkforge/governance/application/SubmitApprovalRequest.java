package com.linkforge.governance.application;

import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;

import java.time.LocalDateTime;

public record SubmitApprovalRequest(
        SensitiveOperationType operationType,
        Long targetApplicationId,
        String beforeSnapshot,
        String afterSnapshot,
        UserActor actor,
        LocalDateTime requestedAt
) {
}
