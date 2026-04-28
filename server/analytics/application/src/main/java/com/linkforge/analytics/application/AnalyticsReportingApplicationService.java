package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnalyticsReportingApplicationService implements AnalyticsReportingService {

    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsLinkSummaryEnricher linkSummaryEnricher;

    public AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            AnalyticsLinkSummaryEnricher linkSummaryEnricher
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.linkSummaryEnricher = linkSummaryEnricher;
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.topLinks(tenantId, from, to, limit, sortBy));
    }

    @Override
    public List<TopLinkStat> applicationTopLinks(
            long tenantId,
            long applicationId,
            LocalDate from,
            LocalDate to,
            int limit,
            TopSortBy sortBy
    ) {
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.applicationTopLinks(tenantId, applicationId, from, to, limit, sortBy));
    }

    @Override
    public List<TopLinkStat> domainTopLinks(
            long tenantId,
            long domainId,
            LocalDate from,
            LocalDate to,
            int limit,
            TopSortBy sortBy
    ) {
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.domainTopLinks(tenantId, domainId, from, to, limit, sortBy));
    }
}
