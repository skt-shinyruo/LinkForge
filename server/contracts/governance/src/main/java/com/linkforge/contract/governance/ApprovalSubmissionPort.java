package com.linkforge.contract.governance;

import java.time.LocalDateTime;
import java.util.Set;

public interface ApprovalSubmissionPort {

    ApprovalRequestView submitRequest(
            long tenantId,
            SensitiveOperation operation,
            Long targetApplicationId,
            String beforeSnapshot,
            String afterSnapshot,
            long actorTenantId,
            long actorUserId,
            String actorEmail,
            Set<String> actorRoles,
            LocalDateTime requestedAt
    );
}
