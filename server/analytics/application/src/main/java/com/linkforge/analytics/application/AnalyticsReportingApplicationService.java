package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsReportingApplicationService implements AnalyticsReportingService {

    private final AnalyticsQueryService analyticsQueryService;
    private final ShortLinkReadService shortLinkReadService;

    public AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            ShortLinkReadService shortLinkReadService
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.shortLinkReadService = shortLinkReadService;
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        return enrich(tenantId, analyticsQueryService.topLinks(tenantId, from, to, limit, sortBy));
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
        return enrich(tenantId, analyticsQueryService.applicationTopLinks(tenantId, applicationId, from, to, limit, sortBy));
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
        return enrich(tenantId, analyticsQueryService.domainTopLinks(tenantId, domainId, from, to, limit, sortBy));
    }

    private List<TopLinkStat> enrich(long tenantId, List<TopLinkStat> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Long> linkIds = rows.stream()
                .map(TopLinkStat::linkId)
                .filter(linkId -> linkId > 0L)
                .distinct()
                .toList();
        Map<Long, ShortLinkReadService.LinkSummary> summaries = shortLinkReadService.listSummaries(
                tenantId,
                List.copyOf(new LinkedHashSet<>(linkIds))
        );

        return rows.stream()
                .map(row -> enrichRow(row, summaries.get(row.linkId())))
                .toList();
    }

    private static TopLinkStat enrichRow(TopLinkStat row, ShortLinkReadService.LinkSummary summary) {
        if (summary == null) {
            return new TopLinkStat(row.linkId(), null, null, row.pv(), row.uv(), true);
        }
        return new TopLinkStat(
                row.linkId(),
                summary.code(),
                summary.originalUrl(),
                row.pv(),
                row.uv(),
                summary.deleted()
        );
    }
}
