package com.linkforge.analytics.infrastructure.persistence;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsDailyStatRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsDimensionRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsQueryMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsTopLinkRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsVisitEventRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** MyBatis-backed analytics read model. */
@Repository
public class AnalyticsQueryRepository implements AnalyticsQueryService {

    private final AnalyticsQueryMapper queryMapper;

    public AnalyticsQueryRepository(AnalyticsQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    @Override
    public List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        return dailyStats(queryMapper.linkDaily(tenantId, linkId, from, to));
    }

    @Override
    public List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        return dailyStats(queryMapper.tenantDaily(tenantId, from, to));
    }

    @Override
    public List<DailyStat> applicationDaily(long tenantId, long applicationId, LocalDate from, LocalDate to) {
        return dailyStats(queryMapper.applicationDaily(tenantId, applicationId, from, to));
    }

    @Override
    public List<DailyStat> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to) {
        return dailyStats(queryMapper.domainDaily(tenantId, domainId, from, to));
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit) {
        return topLinks(tenantId, from, to, limit, TopSortBy.PV);
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        return topLinkStats(sortBy == TopSortBy.UV
                ? queryMapper.topLinksOrderByUv(tenantId, from, to, limit)
                : queryMapper.topLinksOrderByPv(tenantId, from, to, limit));
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
        return topLinkStats(sortBy == TopSortBy.UV
                ? queryMapper.applicationTopLinksOrderByUv(tenantId, applicationId, from, to, limit)
                : queryMapper.applicationTopLinksOrderByPv(tenantId, applicationId, from, to, limit));
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
        return topLinkStats(sortBy == TopSortBy.UV
                ? queryMapper.domainTopLinksOrderByUv(tenantId, domainId, from, to, limit)
                : queryMapper.domainTopLinksOrderByPv(tenantId, domainId, from, to, limit));
    }

    @Override
    public List<DimensionStat> linkDimensions(
            long tenantId,
            long linkId,
            LocalDate from,
            LocalDate to,
            String dimType,
            int limit
    ) {
        String type = dimType == null || dimType.isBlank() ? "unknown" : dimType.trim().toLowerCase();
        long total = safeLong(queryMapper.linkDimTotalPv(tenantId, linkId, from, to, type));
        return safeList(queryMapper.linkDimRows(tenantId, linkId, from, to, type, limit)).stream()
                .map(row -> {
                    long pv = safeLong(row.getPv());
                    return new DimensionStat(
                            row.getValue(),
                            pv,
                            safeLong(row.getUv()),
                            total <= 0 ? 0.0 : pv * 1.0 / total
                    );
                })
                .toList();
    }

    @Override
    public List<VisitEvent> linkEvents(
            long tenantId,
            long linkId,
            LocalDateTime from,
            LocalDateTime to,
            int limit
    ) {
        return safeList(queryMapper.linkEvents(tenantId, linkId, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::visitEvent)
                .toList();
    }

    private static List<DailyStat> dailyStats(List<AnalyticsDailyStatRow> rows) {
        return safeList(rows).stream()
                .map(row -> new DailyStat(row.getDay(), safeLong(row.getPv()), safeLong(row.getUv())))
                .toList();
    }

    private static List<TopLinkStat> topLinkStats(List<AnalyticsTopLinkRow> rows) {
        return safeList(rows).stream()
                .map(row -> row == null
                        ? new TopLinkStat(0L, null, null, 0L, 0L, true)
                        : new TopLinkStat(
                                safeLong(row.getLinkId()),
                                row.getCode(),
                                row.getOriginalUrl(),
                                safeLong(row.getPv()),
                                safeLong(row.getUv()),
                                Boolean.TRUE.equals(row.getDeleted())
                        ))
                .toList();
    }

    private static VisitEvent visitEvent(AnalyticsVisitEventRow row) {
        if (row == null) {
            return new VisitEvent(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        return new VisitEvent(
                row.getOccurredAt(),
                row.getRequestId(),
                row.getIpHash(),
                row.getUserAgentRaw(),
                row.getUserAgentFamily(),
                row.getOsFamily(),
                row.getDeviceType(),
                row.getRefererDomain(),
                row.getLanguage(),
                row.getUtmSource(),
                row.getUtmMedium(),
                row.getUtmCampaign()
        );
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
