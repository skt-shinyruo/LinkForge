import { computed, onMounted, ref } from "vue";
import { listLinks } from "../services/links";
import { fetchLinkDailyStats, fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { DailyStat, LinkDto, TopLinkSortBy, TopLinkStat } from "../services/types";

function toDateUTCString(date: Date) {
  const yyyy = date.getUTCFullYear();
  const mm = String(date.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(date.getUTCDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function calcRange(days: number) {
  const to = new Date();
  const from = new Date(to.getTime());
  from.setUTCDate(from.getUTCDate() - (days - 1));
  return { from: toDateUTCString(from), to: toDateUTCString(to) };
}

function getErrorMessage(caught: unknown, fallbackMessage: string) {
  return caught instanceof Error ? caught.message : fallbackMessage;
}

export function useStatsPage() {
  const error = ref<string | null>(null);
  const loading = ref(false);

  const rangeDays = ref<7 | 30>(7);
  const topSortBy = ref<TopLinkSortBy>("pv");
  const showOverviewChart = ref(false);
  const showLinkChart = ref(false);

  const links = ref<LinkDto[]>([]);
  const selectedLinkId = ref<number | null>(null);
  const linkStats = ref<DailyStat[]>([]);

  const overviewStats = ref<DailyStat[]>([]);
  const topLinks = ref<TopLinkStat[]>([]);

  const selectedLink = computed(() => links.value.find((link) => link.id === selectedLinkId.value) || null);
  const range = computed(() => calcRange(rangeDays.value));

  const overviewChartLabels = computed(() => overviewStats.value.map((stat) => stat.day));
  const overviewChartSeries = computed(() => [
    { name: "PV", data: overviewStats.value.map((stat) => stat.pv) },
    { name: "UV", data: overviewStats.value.map((stat) => stat.uv) },
  ]);

  const linkChartLabels = computed(() => linkStats.value.map((stat) => stat.day));
  const linkChartSeries = computed(() => [
    { name: "PV", data: linkStats.value.map((stat) => stat.pv) },
    { name: "UV", data: linkStats.value.map((stat) => stat.uv) },
  ]);

  async function loadLinks() {
    const response = await listLinks({ page: 0, size: 50 });
    links.value = response.items;
    if (!selectedLinkId.value && links.value.length > 0) {
      selectedLinkId.value = links.value[0]!.id;
    }
  }

  async function loadOverview() {
    overviewStats.value = await fetchOverviewStats(range.value);
  }

  async function loadTopLinks() {
    topLinks.value = await fetchTopLinksStats({
      ...range.value,
      limit: 10,
      sortBy: topSortBy.value,
    });
  }

  async function loadLinkStats() {
    if (!selectedLinkId.value) {
      linkStats.value = [];
      return;
    }
    linkStats.value = await fetchLinkDailyStats(selectedLinkId.value, range.value);
  }

  async function refresh() {
    loading.value = true;
    error.value = null;
    try {
      await loadLinks();
      await Promise.all([loadOverview(), loadTopLinks(), loadLinkStats()]);
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载失败");
    } finally {
      loading.value = false;
    }
  }

  function setRange(days: 7 | 30) {
    rangeDays.value = days;
    void refresh();
  }

  function setTopSortBy(value: TopLinkSortBy) {
    if (topSortBy.value === value) {
      return;
    }
    topSortBy.value = value;
    void loadTopLinks().catch((caught) => {
      error.value = getErrorMessage(caught, "加载 Top 报表失败");
    });
  }

  function onSelectedLinkChange(value: number | null) {
    selectedLinkId.value = value;
    void loadLinkStats().catch((caught) => {
      error.value = getErrorMessage(caught, "加载短链统计失败");
    });
  }

  async function copyShort(code: string | null) {
    if (!code) {
      return;
    }
    const url = `${location.origin}/r/${code}`;
    try {
      await navigator.clipboard.writeText(url);
    } catch {
      // ignore clipboard failures
    }
  }

  onMounted(() => {
    void refresh();
  });

  return {
    error,
    linkChartLabels,
    linkChartSeries,
    linkStats,
    links,
    loading,
    onSelectedLinkChange,
    overviewChartLabels,
    overviewChartSeries,
    overviewStats,
    range,
    rangeDays,
    refresh,
    selectedLink,
    selectedLinkId,
    setRange,
    setTopSortBy,
    showLinkChart,
    showOverviewChart,
    topLinks,
    topSortBy,
    copyShort,
  };
}
