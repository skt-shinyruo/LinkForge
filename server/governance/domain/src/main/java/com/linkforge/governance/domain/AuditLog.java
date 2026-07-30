package com.linkforge.governance.domain;

import java.time.LocalDateTime;

/**
 * Governance 写入的不可变审计事实。
 *
 * <p>审计记录采用追加写语义，保留操作人身份、资源定位、关联审批 ID 以及操作前后快照。
 * 快照是按敏感操作解释的不透明原文，可能为版本化 JSON、历史纯文本或 {@code null}；
 * {@code requestId} 允许为空，以兼容不关联审批请求的治理事件。</p>
 */
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
