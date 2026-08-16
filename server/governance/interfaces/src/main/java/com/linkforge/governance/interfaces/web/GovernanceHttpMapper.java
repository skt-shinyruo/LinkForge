package com.linkforge.governance.interfaces.web;

import com.linkforge.governance.application.ApprovalRequestSummaryResult;
import com.linkforge.governance.application.ApprovalRequestResult;
import com.linkforge.governance.application.AuditLogSummaryResult;

/**
 * Governance 应用结果到稳定 HTTP 结构的纯映射器。
 *
 * <p>枚举使用名称输出，空值保持为空；审批和审计列表响应都刻意不携带 before/after snapshot。
 * 审批决策仍通过按 ID 的权威读取路径加载完整 payload。</p>
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

    /** 将不含 payload 的审批列表读模型转换为安全响应。 */
    static ApprovalRequestHttpResponse toApprovalResponse(ApprovalRequestSummaryResult result) {
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

    /** 将不含前后快照的审计摘要转换为 HTTP 响应。 */
    static AuditLogHttpResponse toAuditLogResponse(AuditLogSummaryResult result) {
        return new AuditLogHttpResponse(
                result.id(),
                result.tenantId(),
                result.actorUserId(),
                result.actorEmail(),
                result.actionType(),
                result.resourceType(),
                result.resourceId(),
                result.requestId(),
                result.createdAt()
        );
    }
}
