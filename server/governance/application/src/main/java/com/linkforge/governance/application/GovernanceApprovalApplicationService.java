package com.linkforge.governance.application;

import com.linkforge.contract.governance.AnalyticsDetailExportApprovalPayload;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalRequestView;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.governance.LinkDestinationChangeApprovalPayload;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.governance.domain.SensitiveOperationType;
import org.springframework.stereotype.Service;

import java.util.Set;

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
        ApprovalRequestResult dto = governanceService.submitRequest(
                tenantId,
                new SubmitApprovalRequest(
                        SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                        request.targetApplicationId(),
                        linkDestinationSnapshot(request.linkId(), request.currentOriginalUrl()),
                        linkDestinationSnapshot(request.linkId(), request.requestedOriginalUrl()),
                        toUserActor(request.requester()),
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
        ApprovalRequestResult dto = governanceService.submitRequest(
                tenantId,
                new SubmitApprovalRequest(
                        SensitiveOperationType.ANALYTICS_DETAIL_EXPORT,
                        request.targetApplicationId(),
                        null,
                        ApprovalPayloadCodec.write(AnalyticsDetailExportApprovalPayload.v1(
                                request.linkId(),
                                request.from(),
                                request.to()
                        )),
                        toUserActor(request.requester()),
                        request.requestedAt()
                )
        );
        return toResult(dto);
    }

    private static String linkDestinationSnapshot(long linkId, String originalUrl) {
        return ApprovalPayloadCodec.write(LinkDestinationChangeApprovalPayload.v1(linkId, originalUrl));
    }

    private static UserActor toUserActor(ApprovalRequester requester) {
        if (requester == null) {
            return null;
        }
        return new UserActor(requester.tenantId(), requester.userId(), requester.email(), Set.of());
    }

    private static ApprovalRequestView toResult(ApprovalRequestResult dto) {
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
