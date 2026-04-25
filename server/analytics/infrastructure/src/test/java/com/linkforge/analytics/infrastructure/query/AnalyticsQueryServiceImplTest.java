package com.linkforge.analytics.infrastructure.query;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.ShortLinkReadService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsQueryServiceImplTest {

    @Test
    void applicationDaily_shouldAggregateUsingCurrentScopeLinkIds() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository, tenantGuard, shortLinkReadService);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(shortLinkReadService.listLinkIdsByApplication(1L, 2001L)).thenReturn(List.of(101L, 102L));
        when(queryRepository.dailyByLinkIds(1L, List.of(101L, 102L), from, to))
                .thenReturn(List.of(new AnalyticsQueryRepository.DailyStatRow(LocalDate.parse("2026-04-02"), 10L, 8L)));

        List<AnalyticsQueryService.DailyStat> actual = service.applicationDaily(1L, 2001L, from, to);

        assertThat(actual).containsExactly(new AnalyticsQueryService.DailyStat(LocalDate.parse("2026-04-02"), 10L, 8L));
        verify(shortLinkReadService).listLinkIdsByApplication(1L, 2001L);
        verify(queryRepository).dailyByLinkIds(1L, List.of(101L, 102L), from, to);
    }

    @Test
    void domainTopLinks_shouldUseCurrentScopeLinkIds() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository, tenantGuard, shortLinkReadService);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(shortLinkReadService.listLinkIdsByDomain(1L, 3001L)).thenReturn(List.of(201L));
        when(queryRepository.topLinksOrderByUvForLinkIds(1L, List.of(201L), from, to, 5))
                .thenReturn(List.of(new AnalyticsQueryRepository.TopLinkRow(201L, null, null, 20L, 18L, false)));

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.domainTopLinks(1L, 3001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV);

        assertThat(actual).containsExactly(new AnalyticsQueryService.TopLinkStat(201L, null, null, 20L, 18L, false));
        verify(shortLinkReadService).listLinkIdsByDomain(1L, 3001L);
        verify(queryRepository).topLinksOrderByUvForLinkIds(1L, List.of(201L), from, to, 5);
    }

    @Test
    void applicationDaily_shouldBatchLargeScopeQueries() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository, tenantGuard, shortLinkReadService);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        List<Long> linkIds = new ArrayList<>();
        for (long linkId = 1L; linkId <= 501L; linkId++) {
            linkIds.add(linkId);
        }
        when(shortLinkReadService.listLinkIdsByApplication(1L, 2001L)).thenReturn(linkIds);
        when(queryRepository.dailyByLinkIds(1L, linkIds.subList(0, 500), from, to))
                .thenReturn(List.of(new AnalyticsQueryRepository.DailyStatRow(LocalDate.parse("2026-04-02"), 10L, 8L)));
        when(queryRepository.dailyByLinkIds(1L, linkIds.subList(500, 501), from, to))
                .thenReturn(List.of(new AnalyticsQueryRepository.DailyStatRow(LocalDate.parse("2026-04-02"), 2L, 1L)));

        List<AnalyticsQueryService.DailyStat> actual = service.applicationDaily(1L, 2001L, from, to);

        assertThat(actual).containsExactly(new AnalyticsQueryService.DailyStat(LocalDate.parse("2026-04-02"), 12L, 9L));
        verify(queryRepository, times(2)).dailyByLinkIds(eqLong(1L), anyList(), eqDate(from), eqDate(to));
    }

    @Test
    void applicationTopLinks_shouldBatchLargeScopeQueriesWithoutSingleHugeInClause() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);
        ShortLinkReadService shortLinkReadService = mock(ShortLinkReadService.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository, tenantGuard, shortLinkReadService);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        List<Long> linkIds = new ArrayList<>();
        for (long linkId = 1L; linkId <= 501L; linkId++) {
            linkIds.add(linkId);
        }
        when(shortLinkReadService.listLinkIdsByApplication(1L, 2001L)).thenReturn(linkIds);
        when(queryRepository.topLinksOrderByPvForLinkIds(1L, linkIds.subList(0, 500), from, to, 2))
                .thenReturn(List.of(
                        new AnalyticsQueryRepository.TopLinkRow(50L, null, null, 30L, 10L, false),
                        new AnalyticsQueryRepository.TopLinkRow(60L, null, null, 20L, 12L, false)
                ));
        when(queryRepository.topLinksOrderByPvForLinkIds(1L, linkIds.subList(500, 501), from, to, 2))
                .thenReturn(List.of(new AnalyticsQueryRepository.TopLinkRow(501L, null, null, 40L, 5L, false)));

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.applicationTopLinks(1L, 2001L, from, to, 2, AnalyticsQueryService.TopSortBy.PV);

        assertThat(actual).containsExactly(
                new AnalyticsQueryService.TopLinkStat(501L, null, null, 40L, 5L, false),
                new AnalyticsQueryService.TopLinkStat(50L, null, null, 30L, 10L, false)
        );
        verify(queryRepository, times(2)).topLinksOrderByPvForLinkIds(eqLong(1L), anyList(), eqDate(from), eqDate(to), eqInt(2));
    }

    private static long eqLong(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private static LocalDate eqDate(LocalDate value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private static int eqInt(int value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
