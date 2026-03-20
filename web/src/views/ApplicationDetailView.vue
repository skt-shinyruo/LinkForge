<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import AppPageShell from "../components/AppPageShell.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { listApplications } from "../services/applications";
import { listApiKeys } from "../services/apiKeys";
import { listDomainsForApplication } from "../services/domains";
import { fetchOverviewStats, fetchTopLinksStats } from "../services/stats";
import type { ApiKeyDto, ApplicationDto, DomainDto, TopLinkStat } from "../services/types";

const navigation = useAppSessionNavigation("applicationDetail");
const route = useRoute();

const loading = ref(false);
const error = ref<string | null>(null);
const application = ref<ApplicationDto | null>(null);
const domains = ref<DomainDto[]>([]);
const apiKeys = ref<ApiKeyDto[]>([]);
const topLinks = ref<TopLinkStat[]>([]);

const applicationId = computed(() => Number(route.params.applicationId));
const recentPv = ref(0);

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

async function load() {
  loading.value = true;
  error.value = null;
  try {
    const [applications, appKeys, overview, nextTopLinks, appDomains] = await Promise.all([
      listApplications(),
      listApiKeys(applicationId.value),
      fetchOverviewStats({ ...buildRange(7), applicationId: applicationId.value }),
      fetchTopLinksStats({ ...buildRange(7), applicationId: applicationId.value, limit: 5, sortBy: "pv" }),
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
</script>

<template>
  <AppPageShell
    :title="navigation.title"
    :user-email="navigation.userEmail.value"
    :nav-items="navigation.navItems"
    @navigate="navigation.navigate"
    @logout="navigation.logout"
  >
    <section class="summary-grid">
      <article class="card">
        <h2>{{ application?.displayName || "未知应用" }}</h2>
        <p class="sub">{{ application?.applicationKey || `ID ${applicationId}` }}</p>
      </article>
      <article class="card">
        <h2>绑定域名</h2>
        <p class="metric">{{ domains.length }}</p>
      </article>
      <article class="card">
        <h2>API Keys</h2>
        <p class="metric">{{ apiKeys.length }}</p>
      </article>
      <article class="card">
        <h2>近 7 天 PV</h2>
        <p class="metric">{{ recentPv }}</p>
      </article>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>Top Links</h2>
        <button class="btn secondary" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
      <ul v-else class="list">
        <li v-for="item in topLinks" :key="item.linkId">
          <strong>{{ item.code || "已删除" }}</strong>
          <span class="sub">PV {{ item.pv }} / UV {{ item.uv }}</span>
        </li>
        <li v-if="topLinks.length === 0" class="sub">暂无统计</li>
      </ul>
    </section>
  </AppPageShell>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric {
  font-size: 28px;
  font-weight: 700;
  margin: 8px 0 0;
}

.list {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 10px;
}

.sub {
  color: #666;
  display: block;
}

.btn {
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: #111;
  color: #fff;
  cursor: pointer;
}

.btn.secondary {
  background: #444;
}

.error {
  color: #c00;
}
</style>
