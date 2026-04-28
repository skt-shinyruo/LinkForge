package com.linkforge.analytics.application;

import com.linkforge.contract.shortlink.ShortLinkReadPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsLinkSummaryEnricherTest {

    @Test
    void enrich_shouldUseShortlinkPublishedReadContractAndPreserveMissingRowsAsDeleted() {
        ShortLinkReadPort shortLinkReadPort = mock(ShortLinkReadPort.class);
        AnalyticsLinkSummaryEnricher enricher = new AnalyticsLinkSummaryEnricher(shortLinkReadPort);
        List<AnalyticsQueryService.TopLinkStat> rows = List.of(
                new AnalyticsQueryService.TopLinkStat(101L, null, null, 50L, 40L, false),
                new AnalyticsQueryService.TopLinkStat(102L, "gone", "https://example.com/gone", 30L, 20L, true)
        );
        when(shortLinkReadPort.listSummaries(1L, List.of(101L, 102L)))
                .thenReturn(Map.of(
                        101L,
                        new ShortLinkReadPort.ShortLinkSummary(101L, "abc123", "https://example.com/a", false)
                ));

        List<AnalyticsQueryService.TopLinkStat> actual = enricher.enrich(1L, rows);

        assertThat(actual).containsExactly(
                new AnalyticsQueryService.TopLinkStat(101L, "abc123", "https://example.com/a", 50L, 40L, false),
                new AnalyticsQueryService.TopLinkStat(102L, "gone", "https://example.com/gone", 30L, 20L, true)
        );
        verify(shortLinkReadPort).listSummaries(1L, List.of(101L, 102L));
    }

    @Test
    void enrich_shouldReturnEmptyListForNullRows() {
        AnalyticsLinkSummaryEnricher enricher = new AnalyticsLinkSummaryEnricher(mock(ShortLinkReadPort.class));

        assertThat(enricher.enrich(1L, null)).isEmpty();
    }
}
