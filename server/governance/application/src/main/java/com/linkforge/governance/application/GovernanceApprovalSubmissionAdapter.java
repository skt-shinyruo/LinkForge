package com.linkforge.governance.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
public class GovernanceApprovalSubmissionAdapter implements ApprovalSubmissionPort {

    private final GovernanceService governanceService;

    public GovernanceApprovalSubmissionAdapter(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override
    public ApprovalRequestView submitRequest(
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
    ) {
        if (actorTenantId != tenantId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "actor 租户不匹配");
        }
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.valueOf(operation.name()),
                        targetApplicationId,
                        beforeSnapshot,
                        afterSnapshot,
                        new UserActor(actorTenantId, actorUserId, actorEmail, actorRoles),
                        requestedAt
                )
        );
        return new ApprovalRequestView(
                dto.id(),
                dto.tenantId(),
                SensitiveOperation.valueOf(dto.operationType().name()),
                dto.targetApplicationId(),
                dto.requestedByUserId(),
                dto.requestedByEmail(),
                dto.status().name(),
                dto.approverUserId(),
                dto.approverEmail(),
                dto.decisionReason()
        );
    }
}
