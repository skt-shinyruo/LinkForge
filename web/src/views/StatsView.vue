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
    <section v-if="navigation.isTenantAdmin.value" class="scope card">
      <h2>统计范围</h2>
      <label class="field">
        <span class="sub">应用</span>
        <select
          :value="page.selectedApplicationId.value ?? ''"
          @change="page.setSelectedApplicationId(($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"
        >
          <option value="">全部应用</option>
          <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
            {{ application.displayName }}
          </option>
        </select>
      </label>
    </section>

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
      :link-search="page.linkSearch.value"
      :link-options-loading="page.linkOptionsLoading.value"
      :link-options-has-more="page.linkOptionsHasMore.value"
      :link-options-error="page.linkOptionsError.value"
      :selected-link-id="page.selectedLinkId.value"
      :selected-link="page.selectedLink.value"
      :link-stats="page.linkStats.value"
      :show-link-chart="page.showLinkChart.value"
      :link-chart-labels="page.linkChartLabels.value"
      :link-chart-series="page.linkChartSeries.value"
      @toggle="page.showLinkChart.value = !page.showLinkChart.value"
      @search-links="page.searchLinks"
      @load-more-links="page.loadMoreLinks"
      @update-link-search="page.linkSearch.value = $event"
      @select-link="page.onSelectedLinkChange"
    />
  </AppPageShell>
</template>

<style scoped>
.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sub {
  color: #666;
  font-size: 12px;
}

select {
  max-width: 320px;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}
</style>
