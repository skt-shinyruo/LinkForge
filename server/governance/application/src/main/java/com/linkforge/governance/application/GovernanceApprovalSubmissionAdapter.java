package com.linkforge.governance.application;

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
            String afterSnapshot
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.valueOf(operation.name()),
                        targetApplicationId,
                        beforeSnapshot,
                        afterSnapshot,
                        resolveCurrentActorReflectively(),
                        LocalDateTime.now()
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

    @SuppressWarnings("unchecked")
    private static UserActor resolveCurrentActorReflectively() {
        try {
            Class<?> authContextClass = Class.forName("com.linkforge.foundation.security.AuthContext");
            Object principal = authContextClass.getMethod("requirePrincipal").invoke(null);
            Class<?> principalClass = principal.getClass();
            long tenantId = ((Number) principalClass.getMethod("getTenantId").invoke(principal)).longValue();
            long userId = ((Number) principalClass.getMethod("getUserId").invoke(principal)).longValue();
            String email = (String) principalClass.getMethod("getEmail").invoke(principal);
            Set<String> roles = (Set<String>) principalClass.getMethod("getRoles").invoke(principal);
            return new UserActor(tenantId, userId, email, roles);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot resolve current actor", ex);
        }
    }
}
