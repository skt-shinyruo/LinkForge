import { computed, getCurrentInstance, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { listApplications } from "../services/applications";
import { listApiKeys } from "../services/apiKeys";
import { listDomainsForApplication } from "../services/domains";
import { fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { ApiKeyDto, ApplicationDto, DomainDto, TopLinkStat } from "../services/types";

function getErrorMessage(caught: unknown, fallbackMessage: string) {
  return caught instanceof Error ? caught.message : fallbackMessage;
}

function buildRange(days: number) {
  const to = new Date();
  const from = new Date(to.getTime());
  from.setUTCDate(from.getUTCDate() - (days - 1));
  const format = (value: Date) => value.toISOString().slice(0, 10);
  return { from: format(from), to: format(to) };
}

export function useApplicationDetailPage() {
  const route = useRoute();

  const loading = ref(false);
  const error = ref<string | null>(null);
  const application = ref<ApplicationDto | null>(null);
  const domains = ref<DomainDto[]>([]);
  const apiKeys = ref<ApiKeyDto[]>([]);
  const topLinks = ref<TopLinkStat[]>([]);
  const recentPv = ref(0);

  const applicationId = computed(() => Number(route.params.applicationId));

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const range = buildRange(7);
      const [applications, appKeys, overview, nextTopLinks, appDomains] = await Promise.all([
        listApplications(),
        listApiKeys(applicationId.value),
        fetchOverviewStats({ ...range, applicationId: applicationId.value }),
        fetchTopLinksStats({ ...range, applicationId: applicationId.value, limit: 5, sortBy: "pv" }),
        listDomainsForApplication(applicationId.value),
      ]);
      application.value =
        applications.find((item) => item.id === applicationId.value) ?? null;
      domains.value = appDomains;
      apiKeys.value = appKeys;
      recentPv.value = overview.reduce((sum, item) => sum + item.pv, 0);
      topLinks.value = nextTopLinks;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载应用详情失败");
    } finally {
      loading.value = false;
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    apiKeys,
    application,
    applicationId,
    domains,
    error,
    load,
    loading,
    recentPv,
    topLinks,
  };
}
