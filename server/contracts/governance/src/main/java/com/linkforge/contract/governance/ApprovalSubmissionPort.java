package com.linkforge.contract.governance;

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
            ApprovalRequester requester,
            LocalDateTime requestedAt
    ) {
    }

    record AnalyticsDetailExportApprovalRequest(
            long linkId,
            Long targetApplicationId,
            LocalDateTime from,
            LocalDateTime to,
            ApprovalRequester requester,
            LocalDateTime requestedAt
    ) {
    }
}
