package com.linkforge.governance.application;

import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;

@Service
public class GovernanceApprovalApplicationService implements ApprovalSubmissionPort {

    private final GovernanceService governanceService;

    public GovernanceApprovalApplicationService(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Override
    public ApprovalRequestView requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    ) {
        GovernanceService.ApprovalRequestDto dto = governanceService.submitRequest(
                tenantId,
                new GovernanceService.SubmitApprovalRequest(
                        SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        request.targetApplicationId(),
                        linkDestinationSnapshot(request.linkId(), request.currentOriginalUrl()),
                        linkDestinationSnapshot(request.linkId(), request.requestedOriginalUrl()),
                        request.actor(),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    @Override
    public ApprovalRequestView requestAnalyticsDetailExportApproval(
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

    private static String linkDestinationSnapshot(long linkId, String originalUrl) {
        return "linkId=" + linkId + "\noriginalUrl=" + originalUrl;
    }

    private static ApprovalRequestView toResult(GovernanceService.ApprovalRequestDto dto) {
        return new ApprovalRequestView(
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
