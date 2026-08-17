package com.linkforge.analytics.infrastructure.persistence;

import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsDailyStatRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsQueryMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.AnalyticsTopLinkRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsQueryRepositoryTest {

    @Test
    void mapsNullableRowsAndKeepsRequestedScopeAndSort() {
        AnalyticsQueryMapper mapper = mock(AnalyticsQueryMapper.class);
        AnalyticsQueryRepository repository = new AnalyticsQueryRepository(mapper);
        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");

        AnalyticsDailyStatRow daily = new AnalyticsDailyStatRow();
        daily.setDay(LocalDate.parse("2026-04-02"));
        daily.setPv(10L);
        when(mapper.applicationDaily(1L, 2001L, from, to)).thenReturn(List.of(daily));

        AnalyticsTopLinkRow top = new AnalyticsTopLinkRow();
        top.setLinkId(201L);
        top.setCode("go201");
        top.setOriginalUrl("https://example.com/201");
        top.setPv(20L);
        top.setUv(18L);
        top.setDeleted(true);
        when(mapper.domainTopLinksOrderByUv(1L, 3001L, from, to, 5)).thenReturn(List.of(top));

        assertThat(repository.applicationDaily(1L, 2001L, from, to))
                .containsExactly(new AnalyticsQueryService.DailyStat(LocalDate.parse("2026-04-02"), 10L, 0L));
        assertThat(repository.domainTopLinks(1L, 3001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV))
                .containsExactly(new AnalyticsQueryService.TopLinkStat(
                        201L, "go201", "https://example.com/201", 20L, 18L, true
                ));
        verify(mapper).applicationDaily(1L, 2001L, from, to);
        verify(mapper).domainTopLinksOrderByUv(1L, 3001L, from, to, 5);
    }

}
