package com.linkforge.governance.application;

import com.linkforge.foundation.context.UserActor;

import java.time.LocalDateTime;

public interface GovernanceApprovalRequestService {

    ApprovalRequestResult requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    );

    ApprovalRequestResult requestAnalyticsDetailExportApproval(
            long tenantId,
            AnalyticsDetailExportApprovalRequest request
    );

    record LinkDestinationChangeApprovalRequest(
            Long targetApplicationId,
            String currentOriginalUrl,
            String requestedOriginalUrl,
            UserActor actor,
            LocalDateTime requestedAt
    ) {
    }

    record AnalyticsDetailExportApprovalRequest(
            long linkId,
            Long targetApplicationId,
            LocalDateTime from,
            LocalDateTime to,
            UserActor actor,
            LocalDateTime requestedAt
    ) {
    }

    record ApprovalRequestResult(
            long id,
            long tenantId,
            String operation,
            Long targetApplicationId,
            long requestedByUserId,
            String requestedByEmail,
            String status,
            Long approverUserId,
            String approverEmail,
            String decisionReason
    ) {
    }
}
