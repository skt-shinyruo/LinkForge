package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsReportingApplicationService implements AnalyticsReportingService {

    private final AnalyticsQueryService analyticsQueryService;
    private final ShortLinkReadPort shortLinkReadPort;

    public AnalyticsReportingApplicationService(
            AnalyticsQueryService analyticsQueryService,
            ShortLinkReadPort shortLinkReadPort
    ) {
        this.analyticsQueryService = analyticsQueryService;
        this.shortLinkReadPort = shortLinkReadPort;
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
        Map<Long, ShortLinkReadPort.ShortLinkSummary> summaries = shortLinkReadPort.listSummaries(
                tenantId,
                List.copyOf(new LinkedHashSet<>(linkIds))
        );

        return rows.stream()
                .map(row -> enrichRow(row, summaries.get(row.linkId())))
                .toList();
    }

    private static TopLinkStat enrichRow(TopLinkStat row, ShortLinkReadPort.ShortLinkSummary summary) {
        if (summary == null) {
            return new TopLinkStat(row.linkId(), row.code(), row.originalUrl(), row.pv(), row.uv(), true);
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
