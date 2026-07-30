package com.linkforge.governance.interfaces.web;

/**
 * 审批接口的安全响应视图。
 *
 * <p>{@code operationType} 和 {@code status} 使用稳定枚举名称；响应不包含 before/after snapshot、
 * 创建时间或执行时间，不能作为完整审计记录使用。</p>
 */
public record ApprovalRequestHttpResponse(
        long id,
        long tenantId,
        String operationType,
        Long targetApplicationId,
        long requestedByUserId,
        String requestedByEmail,
        String status,
        Long approverUserId,
        String approverEmail,
        String decisionReason
) {
}
