package com.linkforge.contract.governance;

import java.time.LocalDateTime;

public final class ApprovalPayloads {

    public static final int VERSION_1 = 1;
    public static final String LINK_DESTINATION_CHANGE = "linkDestinationChange";
    public static final String ANALYTICS_DETAIL_EXPORT = "analyticsDetailExport";
    public static final String APPLICATION_QUOTA_INCREASE = "applicationQuotaIncrease";

    private ApprovalPayloads() {
    }

    public record LinkDestinationChangePayload(
            String type,
            int version,
            long linkId,
            String originalUrl
    ) {

        public static LinkDestinationChangePayload v1(long linkId, String originalUrl) {
            return new LinkDestinationChangePayload(LINK_DESTINATION_CHANGE, VERSION_1, linkId, originalUrl);
        }
    }

    public record AnalyticsDetailExportPayload(
            String type,
            int version,
            long linkId,
            String from,
            String to
    ) {

        public static AnalyticsDetailExportPayload v1(long linkId, LocalDateTime from, LocalDateTime to) {
            return new AnalyticsDetailExportPayload(
                    ANALYTICS_DETAIL_EXPORT,
                    VERSION_1,
                    linkId,
                    from == null ? null : from.toString(),
                    to == null ? null : to.toString()
            );
        }
    }

    public record ApplicationQuotaIncreasePayload(
            String type,
            int version,
            Long monthlyLinkLimit,
            Long monthlyClickLimit
    ) {

        public static ApplicationQuotaIncreasePayload v1(Long monthlyLinkLimit, Long monthlyClickLimit) {
            return new ApplicationQuotaIncreasePayload(
                    APPLICATION_QUOTA_INCREASE,
                    VERSION_1,
                    monthlyLinkLimit,
                    monthlyClickLimit
            );
        }
    }
}
