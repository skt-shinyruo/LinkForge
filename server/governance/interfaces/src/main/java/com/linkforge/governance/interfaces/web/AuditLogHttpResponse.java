package com.linkforge.governance.interfaces.web;

import java.time.LocalDateTime;

public record AuditLogHttpResponse(
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
