package com.linkforge.governance.application;

import java.time.LocalDateTime;

/** 审计列表读模型；大体积前后快照只保留在权威持久化记录中。 */
public record AuditLogSummaryResult(
        long id,
        long tenantId,
        long actorUserId,
        String actorEmail,
        String actionType,
        String resourceType,
        String resourceId,
        Long requestId,
        LocalDateTime createdAt
) {
}
