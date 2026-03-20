package com.linkforge.analytics.infrastructure.query;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsQueryService.DailyStat;
import com.linkforge.analytics.application.AnalyticsQueryService.DimensionStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.analytics.application.AnalyticsQueryService.VisitEvent;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.foundation.runtime.security.TenantGuard;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    private final AnalyticsQueryRepository queryRepository;
    private final TenantGuard tenantGuard;

    public AnalyticsQueryServiceImpl(AnalyticsQueryRepository queryRepository, TenantGuard tenantGuard) {
        this.queryRepository = queryRepository;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public List<DailyStat> linkDaily(long tenantId, long linkId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.linkDaily(tenantId, linkId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> tenantDaily(long tenantId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.tenantDaily(tenantId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> applicationDaily(long tenantId, long applicationId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.applicationDaily(tenantId, applicationId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<DailyStat> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return queryRepository.domainDaily(tenantId, domainId, from, to)
                .stream()
                .map(r -> new DailyStat(r.day(), r.pv(), r.uv()))
                .toList();
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit) {
        tenantGuard.requireCurrentTenant(tenantId);
        return topLinks(tenantId, from, to, limit, TopSortBy.PV);
    }

    @Override
    public List<TopLinkStat> topLinks(long tenantId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.topLinksOrderByUv(tenantId, from, to, limit)
                : queryRepository.topLinksOrderByPv(tenantId, from, to, limit));

        return rows.stream()
                .map(r -> new TopLinkStat(r.linkId(), r.code(), r.originalUrl(), r.pv(), r.uv(), r.deleted()))
                .toList();
    }

    @Override
    public List<TopLinkStat> applicationTopLinks(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.applicationTopLinksOrderByUv(tenantId, applicationId, from, to, limit)
                : queryRepository.applicationTopLinksOrderByPv(tenantId, applicationId, from, to, limit));
        return rows.stream()
                .map(r -> new TopLinkStat(r.linkId(), r.code(), r.originalUrl(), r.pv(), r.uv(), r.deleted()))
                .toList();
    }

    @Override
    public List<TopLinkStat> domainTopLinks(long tenantId, long domainId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        TopSortBy s = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> rows = (s == TopSortBy.UV
                ? queryRepository.domainTopLinksOrderByUv(tenantId, domainId, from, to, limit)
                : queryRepository.domainTopLinksOrderByPv(tenantId, domainId, from, to, limit));
        return rows.stream()
                .map(r -> new TopLinkStat(r.linkId(), r.code(), r.originalUrl(), r.pv(), r.uv(), r.deleted()))
                .toList();
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

    @Override
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

    private static String normalizeDimType(String dimType) {
        if (dimType == null || dimType.isBlank()) {
            return "unknown";
        }
        String t = dimType.trim().toLowerCase();
        return t.isBlank() ? "unknown" : t;
    }
}
