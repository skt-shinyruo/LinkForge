package com.linkforge.analytics.infrastructure.query;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.application.AnalyticsQueryService.DailyStat;
import com.linkforge.analytics.application.AnalyticsQueryService.DimensionStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopLinkStat;
import com.linkforge.analytics.application.AnalyticsQueryService.TopSortBy;
import com.linkforge.analytics.application.AnalyticsQueryService.VisitEvent;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
@Service
public class AnalyticsQueryServiceImpl implements AnalyticsQueryService {

    static final int MAX_SCOPE_LINK_IDS_BATCH_SIZE = 500;

    private final AnalyticsQueryRepository queryRepository;
    private final TenantGuard tenantGuard;
    private final ShortLinkReadService shortLinkReadService;

    public AnalyticsQueryServiceImpl(
            AnalyticsQueryRepository queryRepository,
            TenantGuard tenantGuard,
            ShortLinkReadService shortLinkReadService
    ) {
        this.queryRepository = queryRepository;
        this.tenantGuard = tenantGuard;
        this.shortLinkReadService = shortLinkReadService;
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
        return scopeDaily(tenantId, shortLinkReadService.listLinkIdsByApplication(tenantId, applicationId), from, to);
    }

    @Override
    public List<DailyStat> domainDaily(long tenantId, long domainId, LocalDate from, LocalDate to) {
        tenantGuard.requireCurrentTenant(tenantId);
        return scopeDaily(tenantId, shortLinkReadService.listLinkIdsByDomain(tenantId, domainId), from, to);
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
        return toTopLinkStats(rows);
    }

    @Override
    public List<TopLinkStat> applicationTopLinks(long tenantId, long applicationId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        return scopeTopLinks(
                tenantId,
                shortLinkReadService.listLinkIdsByApplication(tenantId, applicationId),
                from,
                to,
                limit,
                sortBy
        );
    }

    @Override
    public List<TopLinkStat> domainTopLinks(long tenantId, long domainId, LocalDate from, LocalDate to, int limit, TopSortBy sortBy) {
        tenantGuard.requireCurrentTenant(tenantId);
        return scopeTopLinks(
                tenantId,
                shortLinkReadService.listLinkIdsByDomain(tenantId, domainId),
                from,
                to,
                limit,
                sortBy
        );
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

    private List<DailyStat> scopeDaily(long tenantId, List<Long> linkIds, LocalDate from, LocalDate to) {
        List<Long> scopedLinkIds = dedupeLinkIds(linkIds);
        if (scopedLinkIds.isEmpty()) {
            return List.of();
        }

        Map<LocalDate, long[]> totalsByDay = new TreeMap<>();
        for (List<Long> batch : partition(scopedLinkIds)) {
            for (AnalyticsQueryRepository.DailyStatRow row : queryRepository.dailyByLinkIds(tenantId, batch, from, to)) {
                totalsByDay.computeIfAbsent(row.day(), ignored -> new long[2]);
                long[] totals = totalsByDay.get(row.day());
                totals[0] += row.pv();
                totals[1] += row.uv();
            }
        }

        return totalsByDay.entrySet().stream()
                .map(entry -> new DailyStat(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    private List<TopLinkStat> scopeTopLinks(
            long tenantId,
            List<Long> linkIds,
            LocalDate from,
            LocalDate to,
            int limit,
            TopSortBy sortBy
    ) {
        List<Long> scopedLinkIds = dedupeLinkIds(linkIds);
        if (scopedLinkIds.isEmpty()) {
            return List.of();
        }

        TopSortBy effectiveSort = (sortBy == null ? TopSortBy.PV : sortBy);
        List<AnalyticsQueryRepository.TopLinkRow> candidates = new ArrayList<>();
        for (List<Long> batch : partition(scopedLinkIds)) {
            List<AnalyticsQueryRepository.TopLinkRow> rows = (effectiveSort == TopSortBy.UV
                    ? queryRepository.topLinksOrderByUvForLinkIds(tenantId, batch, from, to, limit)
                    : queryRepository.topLinksOrderByPvForLinkIds(tenantId, batch, from, to, limit));
            candidates.addAll(rows);
        }

        Comparator<AnalyticsQueryRepository.TopLinkRow> comparator = effectiveSort == TopSortBy.UV
                ? Comparator.comparingLong(AnalyticsQueryRepository.TopLinkRow::uv).reversed()
                        .thenComparing(Comparator.comparingLong(AnalyticsQueryRepository.TopLinkRow::pv).reversed())
                        .thenComparingLong(AnalyticsQueryRepository.TopLinkRow::linkId)
                : Comparator.comparingLong(AnalyticsQueryRepository.TopLinkRow::pv).reversed()
                        .thenComparing(Comparator.comparingLong(AnalyticsQueryRepository.TopLinkRow::uv).reversed())
                        .thenComparingLong(AnalyticsQueryRepository.TopLinkRow::linkId);

        return candidates.stream()
                .sorted(comparator)
                .limit(limit)
                .map(row -> new TopLinkStat(row.linkId(), row.code(), row.originalUrl(), row.pv(), row.uv(), row.deleted()))
                .toList();
    }

    private static List<Long> dedupeLinkIds(List<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(linkIds));
    }

    private static List<List<Long>> partition(List<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        List<List<Long>> batches = new ArrayList<>((linkIds.size() + MAX_SCOPE_LINK_IDS_BATCH_SIZE - 1) / MAX_SCOPE_LINK_IDS_BATCH_SIZE);
        for (int start = 0; start < linkIds.size(); start += MAX_SCOPE_LINK_IDS_BATCH_SIZE) {
            int end = Math.min(start + MAX_SCOPE_LINK_IDS_BATCH_SIZE, linkIds.size());
            batches.add(linkIds.subList(start, end));
        }
        return List.copyOf(batches);
    }

    private static List<TopLinkStat> toTopLinkStats(List<AnalyticsQueryRepository.TopLinkRow> rows) {
        return rows.stream()
                .map(r -> new TopLinkStat(r.linkId(), r.code(), r.originalUrl(), r.pv(), r.uv(), r.deleted()))
                .toList();
    }
}
