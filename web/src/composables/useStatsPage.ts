import { computed, getCurrentInstance, onMounted, ref } from "vue";
import { listApplications } from "../services/applications";
import { listLinks } from "../services/links";
import { useAuthStore } from "../stores/auth";
import { fetchLinkDailyStats, fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { ApplicationDto, DailyStat, LinkDto, TopLinkSortBy, TopLinkStat } from "../services/types";
import { useLatestRequest } from "./useLatestRequest";

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

/**
 * 统计页异步编排。
 *
 * 链接选项按后端分页完整拉取，随后并行加载 overview、Top links 和选中链接日统计。日期范围按 UTC
 * 自然日构造；应用切换会先重建可选链接再读取报表。函数不把分日 HLL UV 相加为全范围精确 UV。
 *
 * 所有报表读取先形成局部快照，再由 latest-request 控制器一次提交；快速切换不会混入旧响应。
 */
export function useStatsPage() {
  const latestRefresh = useLatestRequest(getErrorMessage);
  const error = latestRefresh.error;
  const loading = latestRefresh.loading;

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

  async function fetchLinksSnapshot(applicationId: number | null, signal: AbortSignal) {
    const nextLinks: LinkDto[] = [];
    let nextPage = 0;
    let totalLinks = 0;

    do {
      const response = await listLinks({
        applicationId: applicationId ?? undefined,
        page: nextPage,
        size: LINK_OPTIONS_PAGE_SIZE,
      }, { signal });
      nextLinks.push(...response.items);
      totalLinks = response.total;
      nextPage += 1;

      if (response.items.length === 0) {
        break;
      }
    } while (nextLinks.length < totalLinks);

    return nextLinks;
  }

  const auth = useAuthStore();
  const isTenantAdmin = computed(() => auth.isTenantAdmin);

  async function loadApplications() {
    if (!isTenantAdmin.value) {
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

  async function setSelectedApplicationId(value: number | null) {
    selectedApplicationId.value = value;
    await refresh();
  }

  async function refresh() {
    const applicationId = selectedApplicationId.value;
    const rangeSnapshot = { ...range.value };
    const sortBy = topSortBy.value;
    const requestedLinkId = selectedLinkId.value;

    await latestRefresh.run(
      async (signal) => {
        const nextLinks = await fetchLinksSnapshot(applicationId, signal);
        const nextSelectedLinkId = nextLinks.some((link) => link.id === requestedLinkId)
          ? requestedLinkId
          : nextLinks[0]?.id ?? null;
        const scopedRange = {
          ...rangeSnapshot,
          applicationId: applicationId ?? undefined,
        };
        const [nextOverview, nextTopLinks, nextLinkStats] = await Promise.all([
          fetchOverviewStats(scopedRange, { signal }),
          fetchTopLinksStats({ ...scopedRange, limit: 10, sortBy }, { signal }),
          nextSelectedLinkId == null
            ? Promise.resolve([] satisfies DailyStat[])
            : fetchLinkDailyStats(nextSelectedLinkId, rangeSnapshot, { signal }),
        ]);
        return {
          linkStats: nextLinkStats,
          links: nextLinks,
          overviewStats: nextOverview,
          selectedLinkId: nextSelectedLinkId,
          topLinks: nextTopLinks,
        };
      },
      (snapshot) => {
        links.value = snapshot.links;
        selectedLinkId.value = snapshot.selectedLinkId;
        overviewStats.value = snapshot.overviewStats;
        topLinks.value = snapshot.topLinks;
        linkStats.value = snapshot.linkStats;
      },
      "加载失败",
    );
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
    void refresh();
  }

  function onSelectedLinkChange(value: number | null) {
    selectedLinkId.value = value;
    void refresh();
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
        if (isTenantAdmin.value) {
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
