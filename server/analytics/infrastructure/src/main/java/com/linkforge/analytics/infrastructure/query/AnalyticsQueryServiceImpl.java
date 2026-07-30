package com.linkforge.analytics.infrastructure.query;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsQueryService.DailyStat;
import com.linkforge.analytics.application.AnalyticsQueryService.DimensionStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.analytics.application.AnalyticsQueryService.VisitEvent;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于 MySQL 日汇总和明细表的报表查询实现。
 *
 * <p>日统计查询的 {@code from}/{@code to} 均为包含端点的 UTC 日期。链接榜单和维度查询对日表的
 * UV 直接求和，跨天或跨链接并不代表全局精确去重；租户、应用、域日统计优先使用范围 HLL 快照表，
 * 缺失时才回退到链接 UV 求和。所有 UV 都是 HyperLogLog 近似值。</p>
 *
 * <p>本层不补齐没有访问的日期，也不在内存重新排序或分页；输入范围、上限和授权由应用服务验证。</p>
 */
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final AnalyticsQueryRepository queryRepository;

    public AnalyticsQueryServiceImpl(AnalyticsQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        return queryRepository.linkDaily(tenantId, linkId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        return queryRepository.tenantDaily(tenantId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> applicationDaily(long tenantId, long applicationId, LocalDate from, LocalDate to) {
        return queryRepository.applicationDaily(tenantId, applicationId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to) {
        return queryRepository.domainDaily(tenantId, domainId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    /**
     * 按 PV 或 UV 查询租户榜单；同分时 SQL 以另一指标和链接 ID 提供稳定排序。
     */
    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit) {
        return topLinks(tenantId, from, to, limit, TopSortBy.PV);
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.topLinksOrderByUv(tenantId, from, to, limit)
                : queryRepository.topLinksOrderByPv(tenantId, from, to, limit));
        return toTopLinkStats(rows);
    }

    @Override
    public List<TopLinkStat> applicationTopLinks(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.applicationTopLinksOrderByUv(tenantId, applicationId, from, to, limit)
                : queryRepository.applicationTopLinksOrderByPv(tenantId, applicationId, from, to, limit));
        return toTopLinkStats(rows);
    }

    @Override
    public List<TopLinkStat> domainTopLinks(long tenantId, long domainId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.domainTopLinksOrderByUv(tenantId, domainId, from, to, limit)
                : queryRepository.domainTopLinksOrderByPv(tenantId, domainId, from, to, limit));
        return toTopLinkStats(rows);
    }

    /**
     * 聚合一个链接某个维度的日表快照。
     *
     * <p>返回的占比以该维度类型在同一区间的 PV 总和为分母；UV 仅作展示，不能相加得到精确独立访客。</p>
     */
    @Override
    public List<DimensionStat> linkDimensions(
            long tenantId,
            long linkId,
            LocalDate from,
            LocalDate to,
            String dimType,
            int limit
    ) {
        String t = normalizeDimType(dimType);

        Long totalPv = queryRepository.linkDimTotalPv(tenantId, linkId, from, to, t);
        long total = totalPv == null ? 0L : totalPv;
        List<AnalyticsQueryRepository.DimensionRow> rows = queryRepository.linkDimRows(tenantId, linkId, from, to, t, limit);

        return rows.stream().map(r -> new DimensionStat(
                r.value(),
                r.pv(),
                r.uv(),
                total <= 0 ? 0.0 : (r.pv() * 1.0 / total)
        )).toList();
    }

    /**
     * 返回按发生时间倒序的已落库明细。该数据受采样和异步落库延迟影响，不等同于实时全量访问日志。
     */
    @Override
    public List<VisitEvent> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit) {
        return queryRepository.linkEvents(tenantId, linkId, from, to, limit)
                .stream()
                .map(r -> new VisitEvent(
                        r.occurredAt(),
                        r.requestId(),
                        r.ipHash(),
                        r.userAgentRaw(),
                        r.userAgentFamily(),
                        r.osFamily(),
                        r.deviceType(),
                        r.refererDomain(),
                        r.language(),
                        r.utmSource(),
                        r.utmMedium(),
                        r.utmCampaign()
                ))
                .toList();
    }

    private static String normalizeDimType(String dimType) {
        if (dimType == null || dimType.isBlank()) {
            return "unknown";
        }
        String t = dimType.trim().toLowerCase();
        return t.isBlank() ? "unknown" : t;
    }

    private static List<TopLinkStat> toTopLinkStats(List<AnalyticsQueryRepository.TopLinkRow> rows) {
        return rows.stream()
                .map(r -> new TopLinkStat(r.linkId(), r.code(), r.originalUrl(), r.pv(), r.uv(), r.deleted()))
                .toList();
    }
}
