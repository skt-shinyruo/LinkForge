package com.linkforge.analytics.interfaces.web;

import com.linkforge.analytics.application.AnalyticsQueryService;

final class AnalyticsHttpMapper {

    private AnalyticsHttpMapper() {
    }

    static DailyStatHttpResponse toDailyStatResponse(AnalyticsQueryService.DailyStat stat) {
        return new DailyStatHttpResponse(stat.day(), stat.pv(), stat.uv());
    }

    static TopLinkStatHttpResponse toTopLinkStatResponse(AnalyticsQueryService.TopLinkStat stat) {
        return new TopLinkStatHttpResponse(
                stat.linkId(),
                stat.code(),
                stat.shortUrl(),
                stat.originalUrl(),
                stat.pv(),
                stat.uv(),
                stat.deleted()
        );
    }

    static DimensionStatHttpResponse toDimensionStatResponse(AnalyticsQueryService.DimensionStat stat) {
        return new DimensionStatHttpResponse(stat.value(), stat.pv(), stat.uv(), stat.ratio());
    }

    static VisitEventHttpResponse toVisitEventResponse(AnalyticsQueryService.VisitEvent event) {
        return new VisitEventHttpResponse(
                event.occurredAt(),
                event.requestId(),
                event.ipHash(),
                event.userAgentRaw(),
                event.userAgentFamily(),
                event.osFamily(),
                event.deviceType(),
                event.refererDomain(),
                event.language(),
                event.utmSource(),
                event.utmMedium(),
                event.utmCampaign()
        );
    }
}
