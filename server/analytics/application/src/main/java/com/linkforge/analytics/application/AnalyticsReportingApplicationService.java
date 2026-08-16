package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Top 链接报表的应用编排服务。
 *
 * <p>先由 Analytics 读模型完成数值排序，再从 Shortlink 读端口补齐展示摘要。该补全不改变 PV/UV，也不重新排序；
 * 删除或 catalog 延迟只会影响链接展示字段和 {@code deleted} 标记。</p>
 */
@Service
public class AnalyticsReportingApplicationService {

    private final AnalyticsQueryService analyticsQueryService;
    private final AnalyticsLinkSummaryEnricher linkSummaryEnricher;

    public AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            AnalyticsLinkSummaryEnricher linkSummaryEnricher
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.linkSummaryEnricher = linkSummaryEnricher;
    }

    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        ReportRange.validate(from, to);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.topLinks(tenantId, from, to, limit, sortBy));
    }

    public List<TopLinkStat> applicationTopLinks(
            long tenantId,
            long applicationId,
            LocalDate from,
            LocalDate to,
            int limit,
            TopSortBy sortBy
    ) {
        ReportRange.validate(from, to);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.applicationTopLinks(tenantId, applicationId, from, to, limit, sortBy));
    }

    public List<TopLinkStat> domainTopLinks(
            long tenantId,
            long domainId,
            LocalDate from,
            LocalDate to,
            int limit,
            TopSortBy sortBy
    ) {
        ReportRange.validate(from, to);
        return linkSummaryEnricher.enrich(tenantId, analyticsQueryService.domainTopLinks(tenantId, domainId, from, to, limit, sortBy));
    }
}
