package com.linkforge.analytics.infrastructure.persistence;

import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsDailyStatRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsDimensionRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsQueryMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsTopLinkRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsVisitEventRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Repository
public class AnalyticsQueryRepository {

    private final AnalyticsQueryMapper queryMapper;

    public AnalyticsQueryRepository(AnalyticsQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    public List<DailyStatRow> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        return safeList(queryMapper.linkDaily(tenantId, linkId, from, to)).stream()
                .map(r -> new DailyStatRow(r.getDay(), safeLong(r.getPv()), safeLong(r.getUv())))
                .toList();
    }

    public List<DailyStatRow> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        return safeList(queryMapper.tenantDaily(tenantId, from, to)).stream()
                .map(r -> new DailyStatRow(r.getDay(), safeLong(r.getPv()), safeLong(r.getUv())))
                .toList();
    }

    public List<DailyStatRow> dailyByLinkIds(long tenantId, List<Long> linkIds, LocalDate from, LocalDate to) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        return safeList(queryMapper.dailyByLinkIds(tenantId, linkIds, from, to)).stream()
                .map(r -> new DailyStatRow(r.getDay(), safeLong(r.getPv()), safeLong(r.getUv())))
                .toList();
    }

    public List<TopLinkRow> topLinksOrderByPv(long tenantId, LocalDate from, LocalDate to, int limit) {
        return safeList(queryMapper.topLinksOrderByPv(tenantId, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::toTopLinkRow)
                .toList();
    }

    public List<TopLinkRow> topLinksOrderByUv(long tenantId, LocalDate from, LocalDate to, int limit) {
        return safeList(queryMapper.topLinksOrderByUv(tenantId, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::toTopLinkRow)
                .toList();
    }

    public List<TopLinkRow> topLinksOrderByPvForLinkIds(long tenantId, List<Long> linkIds, LocalDate from, LocalDate to, int limit) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        return safeList(queryMapper.topLinksOrderByPvForLinkIds(tenantId, linkIds, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::toTopLinkRow)
                .toList();
    }

    public List<TopLinkRow> topLinksOrderByUvForLinkIds(long tenantId, List<Long> linkIds, LocalDate from, LocalDate to, int limit) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        return safeList(queryMapper.topLinksOrderByUvForLinkIds(tenantId, linkIds, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::toTopLinkRow)
                .toList();
    }

    private static TopLinkRow toTopLinkRow(AnalyticsTopLinkRow r) {
        if (r == null) {
            return new TopLinkRow(0L, null, null, 0L, 0L, true);
        }
        long linkId = safeLong(r.getLinkId());
        long pv = safeLong(r.getPv());
        long uv = safeLong(r.getUv());
        String code = r.getCode();
        String originalUrl = r.getOriginalUrl();
        boolean deleted = Boolean.TRUE.equals(r.getDeleted());
        return new TopLinkRow(linkId, code, originalUrl, pv, uv, deleted);
    }

    public Long linkDimTotalPv(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType) {
        return queryMapper.linkDimTotalPv(tenantId, linkId, from, to, dimType);
    }

    public List<DimensionRow> linkDimRows(long tenantId, long linkId, LocalDate from, LocalDate to, String dimType, int limit) {
        return safeList(queryMapper.linkDimRows(tenantId, linkId, from, to, dimType, limit)).stream()
                .map(r -> new DimensionRow(r.getValue(), safeLong(r.getPv()), safeLong(r.getUv())))
                .toList();
    }

    public List<VisitEventRow> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit) {
        return safeList(queryMapper.linkEvents(tenantId, linkId, from, to, limit)).stream()
                .map(AnalyticsQueryRepository::toVisitEventRow)
                .toList();
    }

    private static VisitEventRow toVisitEventRow(AnalyticsVisitEventRow r) {
        if (r == null) {
            return new VisitEventRow(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        return new VisitEventRow(
                r.getOccurredAt(),
                r.getRequestId(),
                r.getIpHash(),
                r.getUserAgentRaw(),
                r.getUserAgentFamily(),
                r.getOsFamily(),
                r.getDeviceType(),
                r.getRefererDomain(),
                r.getLanguage(),
                r.getUtmSource(),
                r.getUtmMedium(),
                r.getUtmCampaign()
        );
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    public record DailyStatRow(LocalDate day, long pv, long uv) {
    }

    public record TopLinkRow(long linkId, String code, String originalUrl, long pv, long uv, boolean deleted) {
    }

    public record DimensionRow(String value, long pv, long uv) {
    }

    public record VisitEventRow(
            LocalDateTime occurredAt,
            String requestId,
            String ipHash,
            String userAgentRaw,
            String userAgentFamily,
            String osFamily,
            String deviceType,
            String refererDomain,
            String language,
            String utmSource,
            String utmMedium,
            String utmCampaign
    ) {
    }
}
