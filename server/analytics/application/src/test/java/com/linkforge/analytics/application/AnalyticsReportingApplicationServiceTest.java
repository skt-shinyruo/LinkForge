package com.linkforge.analytics.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsReportingApplicationServiceTest {

    @Test
    void topLinks_shouldEnrichRawRowsWithShortlinkSummaries() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsReportingService service = new AnalyticsReportingApplicationService(queryService, shortLinkReadPort);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(queryService.topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV))
                .thenReturn(List.of(
                        new AnalyticsQueryService.TopLinkStat(101L, null, null, 50L, 40L, false),
                        new AnalyticsQueryService.TopLinkStat(102L, null, null, 30L, 20L, false)
                ));
        when(shortLinkReadPort.listSummaries(1L, List.of(101L, 102L)))
                .thenReturn(Map.of(
                        101L, new ShortLinkReadPort.ShortLinkSummary(101L, "abc123", "https://example.com/a", false)
                ));

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV);

        assertThat(actual).containsExactly(
                new AnalyticsQueryService.TopLinkStat(101L, "abc123", "https://example.com/a", 50L, 40L, false),
                new AnalyticsQueryService.TopLinkStat(102L, null, null, 30L, 20L, true)
        );
        verify(queryService).topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV);
        verify(shortLinkReadPort).listSummaries(1L, List.of(101L, 102L));
    }

    @Test
    void applicationTopLinks_shouldReuseQueryServiceAndSummaryEnrichment() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsReportingService service = new AnalyticsReportingApplicationService(queryService, shortLinkReadPort);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(queryService.applicationTopLinks(1L, 2001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV))
                .thenReturn(List.of(new AnalyticsQueryService.TopLinkStat(201L, null, null, 10L, 9L, false)));
        when(shortLinkReadPort.listSummaries(1L, List.of(201L)))
                .thenReturn(Map.of(201L, new ShortLinkReadPort.ShortLinkSummary(201L, "go201", "https://example.com/201", false)));

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.applicationTopLinks(1L, 2001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV);

        assertThat(actual).containsExactly(
                new AnalyticsQueryService.TopLinkStat(201L, "go201", "https://example.com/201", 10L, 9L, false)
        );
        verify(queryService).applicationTopLinks(1L, 2001L, from, to, 5, AnalyticsQueryService.TopSortBy.UV);
        verify(shortLinkReadPort).listSummaries(1L, List.of(201L));
    }

    @Test
    void topLinks_shouldKeepCatalogMetadataWhenShortlinkSummaryIsMissingAfterDelete() {
        AnalyticsQueryService queryService = mock(AnalyticsQueryService.class);
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsReportingService service = new AnalyticsReportingApplicationService(queryService, shortLinkReadPort);

        LocalDate from = LocalDate.parse("2026-04-01");
        LocalDate to = LocalDate.parse("2026-04-24");
        when(queryService.topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV))
                .thenReturn(List.of(new AnalyticsQueryService.TopLinkStat(
                        301L,
                        "gone301",
                        "https://example.com/deleted",
                        12L,
                        7L,
                        true
                )));
        when(shortLinkReadPort.listSummaries(1L, List.of(301L))).thenReturn(Map.of());

        List<AnalyticsQueryService.TopLinkStat> actual =
                service.topLinks(1L, from, to, 10, AnalyticsQueryService.TopSortBy.PV);

        assertThat(actual).containsExactly(new AnalyticsQueryService.TopLinkStat(
                301L,
                "gone301",
                "https://example.com/deleted",
                12L,
                7L,
                true
        ));
    }
}
