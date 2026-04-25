package com.linkforge.governance.application;

import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;

@Service
public class GovernanceApprovalApplicationService implements GovernanceApprovalRequestService {

    private final GovernanceService governanceService;

    public GovernanceApprovalApplicationService(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override
    public ApprovalRequestResult requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        request.targetApplicationId(),
                        "originalUrl=" + request.currentOriginalUrl(),
                        "originalUrl=" + request.requestedOriginalUrl(),
                        request.actor(),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    @Override
    public ApprovalRequestResult requestAnalyticsDetailExportApproval(
            long tenantId,
            AnalyticsDetailExportApprovalRequest request
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                        request.targetApplicationId(),
                        null,
                        "linkId=" + request.linkId() + ",from=" + request.from() + ",to=" + request.to(),
                        request.actor(),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    private static ApprovalRequestResult toResult(GovernanceService.ApprovalRequestDto dto) {
        return new ApprovalRequestResult(
                dto.id(),
                dto.tenantId(),
                dto.operationType().name(),
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
