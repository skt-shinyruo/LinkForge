package com.linkforge.governance.interfaces.web;

import com.linkforge.governance.application.ApprovalRequestResult;
import com.linkforge.governance.application.AuditLogResult;

final class GovernanceHttpMapper {

    private GovernanceHttpMapper() {
    }

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
