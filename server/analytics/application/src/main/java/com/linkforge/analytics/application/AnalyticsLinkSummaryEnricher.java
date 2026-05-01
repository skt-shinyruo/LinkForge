package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsLinkSummaryEnricher {

    private final ShortLinkReadPort shortLinkReadPort;

    public AnalyticsLinkSummaryEnricher(ShortLinkReadPort shortLinkReadPort) {
        this.shortLinkReadPort = shortLinkReadPort;
    }

    public List<TopLinkStat> enrich(long tenantId, List<TopLinkStat> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Long> linkIds = rows.stream()
                .map(TopLinkStat::linkId)
                .filter(linkId -> linkId > 0L)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        Map<Long, ShortLinkReadPort.ShortLinkSummary> summaries = shortLinkReadPort.listSummaries(tenantId, linkIds);

        return rows.stream()
                .map(row -> enrichRow(row, summaries.get(row.linkId())))
                .toList();
    }

    private static TopLinkStat enrichRow(TopLinkStat row, ShortLinkReadPort.ShortLinkSummary summary) {
        if (summary == null) {
            return new TopLinkStat(row.linkId(), row.code(), row.shortUrl(), row.originalUrl(), row.pv(), row.uv(), true);
        }
        return new TopLinkStat(
                row.linkId(),
                summary.code(),
                summary.shortUrl(),
                summary.originalUrl(),
                row.pv(),
                row.uv(),
                summary.deleted()
        );
    }
}
