package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.analytics.domain.AggregationPolicy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnalyticsReportingApplicationService implements AnalyticsReportingService {

    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsLinkSummaryEnricher linkSummaryEnricher;
    private final AggregationPolicy aggregationPolicy;

    public AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            AnalyticsLinkSummaryEnricher linkSummaryEnricher
    ) {
        this(analyticsQueryService, linkSummaryEnricher, new AggregationPolicy());
    }

    AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            AnalyticsLinkSummaryEnricher linkSummaryEnricher,
            AggregationPolicy aggregationPolicy
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.linkSummaryEnricher = linkSummaryEnricher;
        this.aggregationPolicy = aggregationPolicy;
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        int effectiveLimit = aggregationPolicy.normalizeLimit(limit, 10, 100);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.topLinks(tenantId, from, to, effectiveLimit, sortBy));
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
        int effectiveLimit = aggregationPolicy.normalizeLimit(limit, 10, 100);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.applicationTopLinks(tenantId, applicationId, from, to, effectiveLimit, sortBy));
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
        int effectiveLimit = aggregationPolicy.normalizeLimit(limit, 10, 100);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.domainTopLinks(tenantId, domainId, from, to, effectiveLimit, sortBy));
    }
}
