package com.linkforge.analytics.application;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsReportingService {

    List<AnalyticsQueryService.TopLinkStat> topLinks(
            long tenantId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );

    List<AnalyticsQueryService.TopLinkStat> applicationTopLinks(
            long tenantId,
            long applicationId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );

    List<AnalyticsQueryService.TopLinkStat> domainTopLinks(
            long tenantId,
            long domainId,
            LocalDate from,
            LocalDate to,
            int limit,
            AnalyticsQueryService.TopSortBy sortBy
    );
}
