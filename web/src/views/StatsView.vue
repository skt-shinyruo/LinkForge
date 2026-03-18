<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import LinkTrendPanel from "../components/stats/LinkTrendPanel.vue";
import StatsOverviewPanel from "../components/stats/StatsOverviewPanel.vue";
import StatsRangeToolbar from "../components/stats/StatsRangeToolbar.vue";
import TopLinksTable from "../components/stats/TopLinksTable.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { useStatsPage } from "../composables/useStatsPage";

const navigation = useAppSessionNavigation("stats");
const page = useStatsPage();
</script>

<template>
  <AppPageShell
    :title="navigation.title"
    :user-email="navigation.userEmail.value"
    :nav-items="navigation.navItems"
    @navigate="navigation.navigate"
    @logout="navigation.logout"
  >
    <StatsRangeToolbar
      :range-days="page.rangeDays.value"
      :range="page.range.value"
      :loading="page.loading.value"
      :error="page.error.value"
      @set-range="page.setRange"
      @refresh="page.refresh"
    />

    <StatsOverviewPanel
      :overview-stats="page.overviewStats.value"
      :overview-chart-labels="page.overviewChartLabels.value"
      :overview-chart-series="page.overviewChartSeries.value"
      :show-overview-chart="page.showOverviewChart.value"
      @toggle="page.showOverviewChart.value = !page.showOverviewChart.value"
    />

    <TopLinksTable
      :top-links="page.topLinks.value"
      :top-sort-by="page.topSortBy.value"
      @set-top-sort-by="page.setTopSortBy"
      @copy-short="page.copyShort"
    />

    <LinkTrendPanel
      :links="page.links.value"
      :selected-link-id="page.selectedLinkId.value"
      :selected-link="page.selectedLink.value"
      :link-stats="page.linkStats.value"
      :show-link-chart="page.showLinkChart.value"
      :link-chart-labels="page.linkChartLabels.value"
      :link-chart-series="page.linkChartSeries.value"
      @toggle="page.showLinkChart.value = !page.showLinkChart.value"
      @select-link="page.onSelectedLinkChange"
    />
  </AppPageShell>
</template>
