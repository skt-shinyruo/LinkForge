package com.linkforge.governance.domain;

import java.time.LocalDateTime;

public record AuditLog(
        long id,
        long tenantId,
        long actorUserId,
        String actorEmail,
        String actionType,
        String resourceType,
        String resourceId,
        Long requestId,
        String beforeSnapshot,
        String afterSnapshot,
        LocalDateTime createdAt
) {
}
