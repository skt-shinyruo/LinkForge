package com.linkforge.governance.interfaces.web;

import java.time.LocalDateTime;

/**
 * 管理员审计查询的 HTTP 响应。
 *
 * <p>before/after snapshot 是未经接口层改写的不透明原文，可能为 JSON、历史纯文本或 {@code null}，
 * 且可能包含敏感业务数据；{@code createdAt} 遵循服务端 UTC {@link LocalDateTime} 约定。</p>
 */
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
