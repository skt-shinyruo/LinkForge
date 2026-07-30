package com.linkforge.analytics.application;

import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将统计读模型中的 Top 链接行补齐为可展示的短链摘要。
 *
 * <p>统计数据与 Shortlink catalog 独立演进。这里批量读取同一租户的权威摘要，保留统计行的原有顺序、PV 和 UV；
 * catalog 缺少某个 ID 时不伪造新链接，而是保留统计快照并标记 {@code deleted=true}，使历史统计仍可展示。</p>
 */
@Service
public class AnalyticsLinkSummaryEnricher {

    private final ShortLinkReadPort shortLinkReadPort;

    public AnalyticsLinkSummaryEnricher(ShortLinkReadPort shortLinkReadPort) {
        this.shortLinkReadPort = shortLinkReadPort;
    }

    /**
     * 对一批 Top 链接行执行一次去重后的跨上下文补全。
     *
     * <p>空输入返回不可变空列表。非正 ID 不会传给 Shortlink 端口，但仍按原行返回并因缺少摘要标记为已删除。</p>
     *
     * @param tenantId 统计行所属租户，必须与 Shortlink 读取范围一致
     * @param rows 查询层返回的统计快照，可为空
     * @return 保留输入顺序的展示行
     */
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
