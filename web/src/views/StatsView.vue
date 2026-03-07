<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from "vue";
import { apiFetch } from "../services/http";
import type { ApiResponse, DailyStat, LinkDto, PageResponse, TopLinkStat } from "../services/types";
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";

const LineChart = defineAsyncComponent(() => import("../components/LineChart.vue"));

const auth = useAuthStore();
const router = useRouter();

const error = ref<string | null>(null);
const loading = ref(false);

const rangeDays = ref<7 | 30>(7);
const topSortBy = ref<"pv" | "uv">("pv");
const showOverviewChart = ref(false);
const showLinkChart = ref(false);

const links = ref<LinkDto[]>([]);
const selectedLinkId = ref<number | null>(null);
const linkStats = ref<DailyStat[]>([]);

const overviewStats = ref<DailyStat[]>([]);
const topLinks = ref<TopLinkStat[]>([]);

const selectedLink = computed(() => links.value.find((l) => l.id === selectedLinkId.value) || null);

function toDateUTCString(d: Date) {
  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(d.getUTCDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function calcRange(days: number) {
  const to = new Date();
  const from = new Date(to.getTime());
  from.setUTCDate(from.getUTCDate() - (days - 1));
  return { from: toDateUTCString(from), to: toDateUTCString(to) };
}

const range = computed(() => calcRange(rangeDays.value));

const overviewChartLabels = computed(() => overviewStats.value.map((s) => s.day));
const overviewChartSeries = computed(() => [
  { name: "PV", data: overviewStats.value.map((s) => s.pv) },
  { name: "UV", data: overviewStats.value.map((s) => s.uv) },
]);

const linkChartLabels = computed(() => linkStats.value.map((s) => s.day));
const linkChartSeries = computed(() => [
  { name: "PV", data: linkStats.value.map((s) => s.pv) },
  { name: "UV", data: linkStats.value.map((s) => s.uv) },
]);

async function loadLinks() {
  const r: ApiResponse<PageResponse<LinkDto>> = await apiFetch<PageResponse<LinkDto>>(
    "/api/v1/links?page=0&size=50",
  );
  if (r.code !== 0) {
    throw new Error(r.message || "加载短链失败");
  }
  links.value = r.data?.items || [];
  if (!selectedLinkId.value && links.value.length > 0) {
    selectedLinkId.value = links.value[0]!.id;
  }
}

async function loadOverview() {
  const { from, to } = range.value;
  const r: ApiResponse<DailyStat[]> = await apiFetch<DailyStat[]>(
    `/api/v1/stats/overview?from=${from}&to=${to}`,
  );
  if (r.code !== 0) {
    throw new Error(r.message || "加载概览失败");
  }
  overviewStats.value = r.data || [];
}

async function loadTopLinks() {
  const { from, to } = range.value;
  const r: ApiResponse<TopLinkStat[]> = await apiFetch<TopLinkStat[]>(
    `/api/v1/stats/top-links?from=${from}&to=${to}&limit=10&sortBy=${topSortBy.value}`,
  );
  if (r.code !== 0) {
    throw new Error(r.message || "加载 Top 报表失败");
  }
  topLinks.value = r.data || [];
}

async function loadLinkStats() {
  if (!selectedLinkId.value) {
    linkStats.value = [];
    return;
  }
  const { from, to } = range.value;
  const r: ApiResponse<DailyStat[]> = await apiFetch<DailyStat[]>(
    `/api/v1/stats/links/${selectedLinkId.value}/daily?from=${from}&to=${to}`,
  );
  if (r.code !== 0) {
    throw new Error(r.message || "加载短链统计失败");
  }
  linkStats.value = r.data || [];
}

function onSelectedLinkChange() {
  void loadLinkStats().catch((e: any) => {
    error.value = e?.message || "加载短链统计失败";
  });
}

async function refresh() {
  loading.value = true;
  error.value = null;
  try {
    await loadLinks();
    await Promise.all([loadOverview(), loadTopLinks(), loadLinkStats()]);
  } catch (e: any) {
    error.value = e?.message || "加载失败";
  } finally {
    loading.value = false;
  }
}

function setRange(d: 7 | 30) {
  rangeDays.value = d;
  refresh();
}

function setTopSortBy(v: "pv" | "uv") {
  if (topSortBy.value === v) {
    return;
  }
  topSortBy.value = v;
  void loadTopLinks().catch((e: any) => {
    error.value = e?.message || "加载 Top 报表失败";
  });
}

function goLinks() {
  router.push("/links");
}

function goTags() {
  router.push("/tags");
}

function logout() {
  auth.logout();
  router.replace("/login");
}

async function copyShort(code: string | null) {
  if (!code) {
    return;
  }
  const url = `${location.origin}/r/${code}`;
  try {
    await navigator.clipboard.writeText(url);
  } catch {
    // ignore
  }
}

onMounted(refresh);
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>统计看板</h1>
        <p class="sub">当前用户：{{ auth.email }}</p>
      </div>
      <div class="actions">
        <button class="btn secondary" @click="goLinks">短链</button>
        <button class="btn secondary" @click="goTags">标签</button>
        <button class="btn secondary" @click="logout">退出</button>
      </div>
    </header>

    <section class="card">
      <div class="toolbar">
        <div class="range">
          <span class="label">时间范围</span>
          <button class="btn small" :class="rangeDays === 7 ? 'active' : ''" @click="setRange(7)">
            近 7 天
          </button>
          <button class="btn small" :class="rangeDays === 30 ? 'active' : ''" @click="setRange(30)">
            近 30 天
          </button>
          <span class="sub">{{ range.from }} ~ {{ range.to }}（UTC）</span>
        </div>
        <button class="btn secondary" :disabled="loading" @click="refresh">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <div class="cardHead">
        <h2>租户趋势（PV / UV）</h2>
        <button
          class="btn small secondary"
          :disabled="overviewStats.length === 0"
          @click="showOverviewChart = !showOverviewChart"
        >
          {{ showOverviewChart ? "收起图表" : "显示图表" }}
        </button>
      </div>
      <div v-if="overviewStats.length === 0" class="sub">暂无数据（请先访问短链，并等待/触发聚合落库）。</div>
      <div v-else-if="!showOverviewChart" class="sub">
        图表已拆分为按需加载，点击“显示图表”后再加载图表组件。
      </div>
      <Suspense v-else>
        <template #default>
          <LineChart title="" :labels="overviewChartLabels" :series="overviewChartSeries" height="320px" />
        </template>
        <template #fallback>
          <div class="sub">图表加载中...</div>
        </template>
      </Suspense>
    </section>

    <section class="card">
      <div class="cardHead">
        <h2>Top 链接（按 {{ topSortBy.toUpperCase() }}）</h2>
        <div class="range">
          <span class="label">排序</span>
          <button class="btn small" :class="topSortBy === 'pv' ? 'active' : ''" @click="setTopSortBy('pv')">
            按 PV
          </button>
          <button class="btn small" :class="topSortBy === 'uv' ? 'active' : ''" @click="setTopSortBy('uv')">
            按 UV
          </button>
        </div>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>#</th>
            <th>短码</th>
            <th>短链</th>
            <th>原始链接</th>
            <th>PV</th>
            <th>UV</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(t, idx) in topLinks" :key="t.linkId">
            <td class="mono">{{ idx + 1 }}</td>
            <td class="mono">
              <span v-if="t.code">{{ t.code }}</span>
              <span v-else class="sub">已删除</span>
            </td>
            <td class="mono">
              <a v-if="t.code" :href="`/r/${t.code}`" target="_blank" rel="noreferrer">/r/{{ t.code }}</a>
              <span v-else class="sub">已删除</span>
            </td>
            <td class="mono">
              <span v-if="t.originalUrl">{{ t.originalUrl }}</span>
              <span v-else class="sub">-</span>
            </td>
            <td class="mono">{{ t.pv }}</td>
            <td class="mono">{{ t.uv }}</td>
            <td>
              <button class="btn small secondary" :disabled="!t.code" @click="copyShort(t.code)">复制</button>
            </td>
          </tr>
          <tr v-if="topLinks.length === 0">
            <td colspan="7" class="sub">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card">
      <div class="cardHead">
        <h2>单短链趋势（PV / UV）</h2>
        <button
          class="btn small secondary"
          :disabled="!selectedLinkId || linkStats.length === 0"
          @click="showLinkChart = !showLinkChart"
        >
          {{ showLinkChart ? "收起图表" : "显示图表" }}
        </button>
      </div>
      <label class="field">
        选择短链
        <select v-model.number="selectedLinkId" @change="onSelectedLinkChange">
          <option v-for="l in links" :key="l.id" :value="l.id">{{ l.code }} - {{ l.originalUrl }}</option>
        </select>
      </label>
      <div v-if="!selectedLinkId" class="sub">请先选择短链。</div>
      <div v-else-if="linkStats.length === 0" class="sub">暂无数据（请先访问该短链，并等待/触发聚合落库）。</div>
      <div v-else-if="!showLinkChart" class="sub">
        当前短链：<span class="mono">{{ selectedLink?.code || selectedLinkId }}</span>
        图表已拆分为按需加载，点击“显示图表”后再加载图表组件。
      </div>
      <Suspense v-else>
        <template #default>
          <LineChart title="" :labels="linkChartLabels" :series="linkChartSeries" height="320px" />
        </template>
        <template #fallback>
          <div class="sub">图表加载中...</div>
        </template>
      </Suspense>
    </section>

    <section class="card">
      <h2>单短链明细（按天）</h2>
      <table class="table">
        <thead>
          <tr>
            <th>日期</th>
            <th>PV</th>
            <th>UV</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in linkStats" :key="s.day">
            <td>{{ s.day }}</td>
            <td class="mono">{{ s.pv }}</td>
            <td class="mono">{{ s.uv }}</td>
          </tr>
          <tr v-if="linkStats.length === 0">
            <td colspan="3" class="sub">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 24px auto;
  padding: 0 16px;
}
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.sub {
  color: #666;
  margin: 4px 0 0;
}
.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}
.cardHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.range {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.label {
  font-size: 14px;
  color: #111;
}
.field {
  display: grid;
  gap: 6px;
  font-size: 14px;
  margin-bottom: 12px;
}
select {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}
.error {
  color: #c00;
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
.btn.small {
  padding: 6px 10px;
  font-size: 12px;
}
.btn.active {
  background: #111;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th,
.table td {
  border-top: 1px solid #eee;
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New",
    monospace;
  font-size: 12px;
}
</style>
