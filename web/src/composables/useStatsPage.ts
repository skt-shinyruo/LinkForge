import { computed, ref } from "vue";
import { listApplications } from "../services/applications";
import { listLinks } from "../services/links";
import { useAuthStore } from "../stores/auth";
import { fetchLinkDailyStats, fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { ApplicationDto, DailyStat, LinkDto, TopLinkSortBy, TopLinkStat } from "../services/types";
import { useLatestRequest } from "./useLatestRequest";

const LINK_OPTIONS_PAGE_SIZE = 20;

function calcRange(days: number) {
  const to = new Date();
  const from = new Date(to.getTime());
  from.setUTCDate(from.getUTCDate() - (days - 1));
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

function getErrorMessage(caught: unknown, fallbackMessage: string) {
  return caught instanceof Error ? caught.message : fallbackMessage;
}

/**
 * 统计页异步编排。
 *
 * 链接选项通过有界 keyset 页按需搜索；overview、Top links 和选中链接日统计拥有独立刷新路径。日期范围按
 * UTC 自然日构造；应用切换会先重建可选链接再读取报表。函数不把分日 HLL UV 相加为全范围精确 UV。
 *
 * 所有报表读取先形成局部快照，再由 latest-request 控制器一次提交；快速切换不会混入旧响应。
 */
export function useStatsPage() {
  const latestOverview = useLatestRequest();
  const latestLinkOptions = useLatestRequest();
  const latestLinkTrend = useLatestRequest();
  const latestTopLinks = useLatestRequest();
  const applicationError = ref<string | null>(null);
  const error = computed(() =>
    applicationError.value ||
    latestOverview.error.value ||
    latestLinkOptions.error.value ||
    latestLinkTrend.error.value ||
    latestTopLinks.error.value,
  );
  const loading = computed(() =>
    latestOverview.loading.value || latestLinkTrend.loading.value || latestTopLinks.loading.value,
  );

  const rangeDays = ref<7 | 30>(7);
  const topSortBy = ref<TopLinkSortBy>("pv");
  const applications = ref<ApplicationDto[]>([]);
  const selectedApplicationId = ref<number | null>(null);
  const links = ref<LinkDto[]>([]);
  const linkSearch = ref("");
  const appliedLinkSearch = ref("");
  const nextLinkCursor = ref<string | null>(null);
  const linkOptionsHasMore = ref(false);
  const selectedLinkId = ref<number | null>(null);
  const linkStats = ref<DailyStat[]>([]);

  const overviewStats = ref<DailyStat[]>([]);
  const topLinks = ref<TopLinkStat[]>([]);

  const range = computed(() => calcRange(rangeDays.value));

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
    const applicationChanged = selectedApplicationId.value !== value;
    selectedApplicationId.value = value;
    if (applicationChanged) {
      latestLinkTrend.cancel();
      links.value = [];
      appliedLinkSearch.value = "";
      nextLinkCursor.value = null;
      linkOptionsHasMore.value = false;
      selectedLinkId.value = null;
      linkStats.value = [];
    }
    await searchLinks();
    await refreshReports(false);
  }

  function normalizedLinkSearch() {
    const value = linkSearch.value.trim();
    return value || undefined;
  }

  async function searchLinks() {
    const applicationId = selectedApplicationId.value;
    const keyword = normalizedLinkSearch();
    await latestLinkOptions.run(
      (signal) => listLinks({
        applicationId: applicationId ?? undefined,
        cursor: undefined,
        includeTotal: false,
        keyword,
        size: LINK_OPTIONS_PAGE_SIZE,
      }, { signal }),
      (response) => {
        links.value = response.items;
        appliedLinkSearch.value = keyword || "";
        nextLinkCursor.value = response.nextCursor ?? null;
        linkOptionsHasMore.value = response.hasMore === true && nextLinkCursor.value != null;
        if (!links.value.some((link) => link.id === selectedLinkId.value)) {
          selectedLinkId.value = links.value[0]?.id ?? null;
          linkStats.value = [];
        }
      },
      "加载短链选项失败",
    );
    await refreshLinkStats();
  }

  async function loadMoreLinks() {
    const cursor = nextLinkCursor.value;
    if (!cursor || latestLinkOptions.loading.value) {
      return;
    }
    const applicationId = selectedApplicationId.value;
    const keyword = appliedLinkSearch.value || undefined;
    await latestLinkOptions.run(
      (signal) => listLinks({
        applicationId: applicationId ?? undefined,
        cursor,
        includeTotal: false,
        keyword,
        size: LINK_OPTIONS_PAGE_SIZE,
      }, { signal }),
      (response) => {
        const existingIds = new Set(links.value.map((link) => link.id));
        links.value = [...links.value, ...response.items.filter((link) => !existingIds.has(link.id))];
        nextLinkCursor.value = response.nextCursor ?? null;
        linkOptionsHasMore.value = response.hasMore === true && nextLinkCursor.value != null;
      },
      "加载更多短链失败",
    );
  }

  async function refreshReports(includeLinkTrend: boolean) {
    const requests = [refreshOverview(), refreshTopLinks()];
    if (includeLinkTrend) {
      requests.push(refreshLinkStats());
    }
    await Promise.all(requests);
  }

  async function refresh() {
    await refreshReports(true);
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
    void refreshTopLinks();
  }

  async function refreshOverview() {
    const scopedRange = {
      ...range.value,
      applicationId: selectedApplicationId.value ?? undefined,
    };
    await latestOverview.run(
      (signal) => fetchOverviewStats(scopedRange, { signal }),
      (result) => {
        overviewStats.value = result;
      },
      "加载概览失败",
    );
  }

  async function refreshTopLinks() {
    const scopedRange = {
      ...range.value,
      applicationId: selectedApplicationId.value ?? undefined,
      limit: 10,
      sortBy: topSortBy.value,
    };
    await latestTopLinks.run(
      (signal) => fetchTopLinksStats(scopedRange, { signal }),
      (result) => {
        topLinks.value = result;
      },
      "加载 Top 链接失败",
    );
  }

  async function refreshLinkStats() {
    const linkId = selectedLinkId.value;
    if (linkId == null) {
      linkStats.value = [];
      latestLinkTrend.cancel();
      return;
    }
    const rangeSnapshot = { ...range.value };
    await latestLinkTrend.run(
      (signal) => fetchLinkDailyStats(linkId, rangeSnapshot, { signal }),
      (result) => {
        linkStats.value = result;
      },
      "加载短链趋势失败",
    );
  }

  async function onSelectedLinkChange(value: number | null) {
    selectedLinkId.value = value;
    await refreshLinkStats();
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

  async function init() {
    if (isTenantAdmin.value) {
      try {
        await loadApplications();
      } catch (caught) {
        applicationError.value = getErrorMessage(caught, "加载应用失败");
      }
    }
    await searchLinks();
    await refreshReports(false);
  }

  return {
    applications,
    error,
    init,
    linkStats,
    linkOptionsError: latestLinkOptions.error,
    linkOptionsHasMore,
    linkOptionsLoading: latestLinkOptions.loading,
    linkSearch,
    links,
    loadMoreLinks,
    loading,
    onSelectedLinkChange,
    overviewStats,
    range,
    rangeDays,
    refresh,
    searchLinks,
    selectedApplicationId,
    selectedLinkId,
    setRange,
    setSelectedApplicationId,
    setTopSortBy,
    topLinks,
    topSortBy,
    copyShort,
  };
}
