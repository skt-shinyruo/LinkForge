package com.linkforge.analytics.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsQueryService {

    List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to);

    List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to);

    List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit);

    List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy);

    List<DimensionStat> linkDimensions(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType, int limit);

    List<VisitEvent> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit);

    record DailyStat(LocalDate day, long pv, long uv) {
    }

    enum TopSortBy {
        PV,
        UV
    }

    record TopLinkStat(long linkId, String code, String originalUrl, long pv, long uv, boolean deleted) {
    }

    record DimensionStat(String value, long pv, long uv, double ratio) {
    }

    record VisitEvent(
            LocalDateTime occurredAt,
            String requestId,
            String ipHash,
            String userAgentRaw,
            String userAgentFamily,
            String osFamily,
            String deviceType,
            String refererDomain,
            String language,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {
    }
}

