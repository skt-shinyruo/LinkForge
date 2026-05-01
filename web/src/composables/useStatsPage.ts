import { computed, getCurrentInstance, onMounted, ref } from "vue";
import { listApplications } from "../services/applications";
import { listLinks } from "../services/links";
import { useAuthStore } from "../stores/auth";
import { fetchLinkDailyStats, fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { ApplicationDto, DailyStat, LinkDto, TopLinkSortBy, TopLinkStat } from "../services/types";

const LINK_OPTIONS_PAGE_SIZE = 100;

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

  const applications = ref<ApplicationDto[]>([]);
  const selectedApplicationId = ref<number | null>(null);
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
    const nextLinks: LinkDto[] = [];
    let nextPage = 0;
    let totalLinks = 0;

    do {
      const response = await listLinks({
        applicationId: selectedApplicationId.value ?? undefined,
        page: nextPage,
        size: LINK_OPTIONS_PAGE_SIZE,
      });
      nextLinks.push(...response.items);
      totalLinks = response.total;
      nextPage += 1;

      if (response.items.length === 0) {
        break;
      }
    } while (nextLinks.length < totalLinks);

    links.value = nextLinks;

    if (links.value.length === 0) {
      selectedLinkId.value = null;
      return;
    }

    if (!links.value.some((link) => link.id === selectedLinkId.value)) {
      selectedLinkId.value = links.value[0]!.id;
    }
  }

  const auth = useAuthStore();
  const isAdmin = computed(() => auth.isAdmin);

  async function loadApplications() {
    if (!isAdmin.value) {
      applications.value = [];
      selectedApplicationId.value = null;
      return;
    }
    applications.value = await listApplications();
    if (
      selectedApplicationId.value != null &&
      !applications.value.some((application) => application.id === selectedApplicationId.value)
    ) {
      selectedApplicationId.value = null;
    }
  }

  async function loadOverview() {
    overviewStats.value = await fetchOverviewStats({
      ...range.value,
      applicationId: selectedApplicationId.value ?? undefined,
    });
  }

  async function loadTopLinks() {
    topLinks.value = await fetchTopLinksStats({
      ...range.value,
      applicationId: selectedApplicationId.value ?? undefined,
      limit: 10,
      sortBy: topSortBy.value,
    });
  }

  async function setSelectedApplicationId(value: number | null) {
    selectedApplicationId.value = value;
    await refresh();
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

  async function copyShort(shortUrl: string | null) {
    if (!shortUrl) {
      return;
    }
    try {
      await navigator.clipboard.writeText(shortUrl);
    } catch {
      // ignore clipboard failures
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void (async () => {
        if (isAdmin.value) {
          try {
            await loadApplications();
          } catch (caught) {
            error.value = getErrorMessage(caught, "加载应用失败");
          }
        }
        await refresh();
      })();
    });
  }

  return {
    applications,
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
    selectedApplicationId,
    selectedLinkId,
    setRange,
    setSelectedApplicationId,
    setTopSortBy,
    showLinkChart,
    showOverviewChart,
    topLinks,
    topSortBy,
    copyShort,
  };
}
