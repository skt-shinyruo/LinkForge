package com.linkforge.analytics.application;

import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.redirect.LinkMetaQueryPort;
import com.linkforge.foundation.security.TenantGuard;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnalyticsQueryService {

    private final AnalyticsQueryRepository queryRepository;
    private final TenantGuard tenantGuard;
    private final LinkMetaQueryPort linkMetaQuery;

    public AnalyticsQueryService(AnalyticsQueryRepository queryRepository, TenantGuard tenantGuard, LinkMetaQueryPort linkMetaQuery) {
        this.queryRepository = queryRepository;
        this.tenantGuard = tenantGuard;
        this.linkMetaQuery = linkMetaQuery;
    }

    public List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.linkDaily(tenantId, linkId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    public List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.tenantDaily(tenantId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit) {
        tenantGuard.requireCurrentTenant(tenantId);
        return topLinks(tenantId, from, to, limit, TopSortBy.PV);
    }

    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkAggRow> rows = (s == TopSortBy.UV
                ? queryRepository.topLinksOrderByUv(tenantId, from, to, limit)
                : queryRepository.topLinksOrderByPv(tenantId, from, to, limit));

        return rows.stream().map(r -> {
            LinkMeta meta = linkMetaQuery.findById(tenantId, r.linkId()).orElse(null);
            if (meta == null) {
                return new TopLinkStat(r.linkId(), null, null, r.pv(), r.uv(), true);
            }
            return new TopLinkStat(r.linkId(), meta.code(), meta.originalUrl(), r.pv(), r.uv(), false);
        }).toList();
    }

    public List<DimensionStat> linkDimensions(
            long tenantId,
            long linkId,
            LocalDate from,
            LocalDate to,
            String dimType,
            int limit
    ) {
        tenantGuard.requireCurrentTenant(tenantId);
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

    public List<VisitEvent> linkEvents(long tenantId, long linkId, LocalDateTime from, LocalDateTime to, int limit) {
        tenantGuard.requireCurrentTenant(tenantId);
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

    public record DailyStat(LocalDate day, long pv, long uv) {
    }

    public enum TopSortBy {
        PV,
        UV
    }

    public record TopLinkStat(long linkId, String code, String originalUrl, long pv, long uv, boolean deleted) {
    }

    public record DimensionStat(String value, long pv, long uv, double ratio) {
    }

    public record VisitEvent(
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

    private static String normalizeDimType(String dimType) {
        if (dimType == null || dimType.isBlank()) {
            return "unknown";
        }
        String t = dimType.trim().toLowerCase();
        return t.isBlank() ? "unknown" : t;
    }
}
