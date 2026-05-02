package com.linkforge.analytics.infrastructure.query;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsQueryServiceImplTest {

    @Test
    void applicationDaily_shouldUseAnalyticsCatalogScope() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(queryRepository.applicationDaily(1L, 2001L, from, to))
                .thenReturn(List.of(new AnalyticsQueryRepository.DailyStatRow(LocalDate.parse("2026-04-02"), 10L, 8L)));

        List<AnalyticsQueryService.DailyStat> actual = service.applicationDaily(1L, 2001L, from, to);

        assertThat(actual).containsExactly(new AnalyticsQueryService.DailyStat(LocalDate.parse("2026-04-02"), 10L, 8L));
        verify(queryRepository).applicationDaily(1L, 2001L, from, to);
    }

    @Test
    void domainTopLinks_shouldUseAnalyticsCatalogScope() {
        AnalyticsQueryRepository queryRepository = mock(AnalyticsQueryRepository.class);
        AnalyticsQueryServiceImpl service = new AnalyticsQueryServiceImpl(queryRepository);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(queryRepository.domainTopLinksOrderByUv(1L, 3001L, from, to, 5))
                .thenReturn(List.of(new AnalyticsQueryRepository.TopLinkRow(201L, "go201", "https://example.com/201", 20L, 18L, true)));

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.domainTopLinks(1L, 3001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV);

        assertThat(actual).containsExactly(
                new AnalyticsQueryService.TopLinkStat(201L, "go201", "https://example.com/201", 20L, 18L, true)
        );
        verify(queryRepository).domainTopLinksOrderByUv(1L, 3001L, from, to, 5);
    }
}
