package com.linkforge.contract.governance;

import java.time.LocalDateTime;

public record AnalyticsDetailExportApprovalPayload(
        String type,
        int version,
        long linkId,
        String from,
        String to
) {

    public static AnalyticsDetailExportApprovalPayload v1(long linkId, LocalDateTime from, LocalDateTime to) {
        return new AnalyticsDetailExportApprovalPayload(
                ApprovalPayloadTypes.ANALYTICS_DETAIL_EXPORT,
                ApprovalPayloadTypes.VERSION_1,
                linkId,
                from == null ? null : from.toString(),
                to == null ? null : to.toString()
        );
    }
}
