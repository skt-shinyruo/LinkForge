package com.linkforge.contract.governance;

import com.linkforge.foundation.context.UserActor;

import java.time.LocalDateTime;

public interface ApprovalSubmissionPort {

    ApprovalRequestView requestLinkDestinationChangeApproval(
            long tenantId,
            LinkDestinationChangeApprovalRequest request
    );

    ApprovalRequestView requestAnalyticsDetailExportApproval(
            long tenantId,
            AnalyticsDetailExportApprovalRequest request
    );

    record LinkDestinationChangeApprovalRequest(
            long linkId,
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
}
