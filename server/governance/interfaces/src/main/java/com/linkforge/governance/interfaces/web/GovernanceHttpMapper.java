package com.linkforge.governance.interfaces.web;

import com.linkforge.governance.application.ApprovalRequestResult;
import com.linkforge.governance.application.AuditLogResult;

/**
 * Governance 应用结果到稳定 HTTP 结构的纯映射器。
 *
 * <p>枚举使用名称输出，空值保持为空；审批响应刻意不携带 before/after snapshot，而审计响应保留
 * 原始快照供授权管理员排障。</p>
 */
final class GovernanceHttpMapper {

    private GovernanceHttpMapper() {
    }

    /** 将审批安全视图转换为 HTTP 响应。 */
    static ApprovalRequestHttpResponse toApprovalResponse(ApprovalRequestResult result) {
        return new ApprovalRequestHttpResponse(
                result.id(),
                result.tenantId(),
                result.operationType() == null ? null : result.operationType().name(),
                result.targetApplicationId(),
                result.requestedByUserId(),
                result.requestedByEmail(),
                result.status() == null ? null : result.status().name(),
                result.approverUserId(),
                result.approverEmail(),
                result.decisionReason()
        );
    }

    /** 将审计结果原样转换为包含快照的 HTTP 响应。 */
    static AuditLogHttpResponse toAuditLogResponse(AuditLogResult result) {
        return new AuditLogHttpResponse(
                result.id(),
                result.tenantId(),
                result.actorUserId(),
                result.actorEmail(),
                result.actionType(),
                result.resourceType(),
                result.resourceId(),
                result.requestId(),
                result.beforeSnapshot(),
                result.afterSnapshot(),
                result.createdAt()
        );
    }
}
