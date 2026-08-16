<script setup lang="ts">
import { defineAsyncComponent } from "vue";

const LineChart = defineAsyncComponent(() => import("../LineChart.vue"));

defineProps<{
  links: { id: number; code: string; originalUrl: string }[];
  linkSearch: string;
  linkOptionsLoading: boolean;
  linkOptionsHasMore: boolean;
  linkOptionsError: string | null;
  selectedLinkId: number | null;
  selectedLink: { id: number; code: string; originalUrl: string } | null;
  linkStats: { day: string; pv: number; uv: number }[];
  showLinkChart: boolean;
  linkChartLabels: string[];
  linkChartSeries: { name: string; data: number[] }[];
}>();

defineEmits<{
  toggle: [];
  searchLinks: [];
  loadMoreLinks: [];
  updateLinkSearch: [value: string];
  selectLink: [value: number | null];
}>();
</script>

<template>
  <section class="card">
    <div class="card-head">
      <h2>单短链趋势（PV / UV）</h2>
      <button class="btn small secondary" :disabled="!selectedLinkId || linkStats.length === 0" @click="$emit('toggle')">
        {{ showLinkChart ? "收起图表" : "显示图表" }}
      </button>
    </div>
    <form class="linkSearch" @submit.prevent="$emit('searchLinks')">
      <input
        type="search"
        :value="linkSearch"
        placeholder="短码或原始链接"
        @input="$emit('updateLinkSearch', ($event.target as HTMLInputElement).value)"
      />
      <button class="btn small secondary stateButton searchButton" type="submit" :disabled="linkOptionsLoading">
        {{ linkOptionsLoading ? "搜索中..." : "搜索" }}
      </button>
    </form>
    <div v-if="linkOptionsError" class="error">{{ linkOptionsError }}</div>
    <label class="field">
      选择短链
      <select :value="selectedLinkId ?? ''" @change="$emit('selectLink', Number(($event.target as HTMLSelectElement).value) || null)">
        <option v-if="links.length === 0" value="" disabled>暂无匹配短链</option>
        <option v-for="link in links" :key="link.id" :value="link.id">{{ link.code }} - {{ link.originalUrl }}</option>
      </select>
    </label>
    <button
      v-if="linkOptionsHasMore"
      class="btn small secondary stateButton loadMore"
      type="button"
      :disabled="linkOptionsLoading"
      @click="$emit('loadMoreLinks')"
    >
      {{ linkOptionsLoading ? "加载中..." : "加载更多" }}
    </button>
    <div v-if="!selectedLinkId" class="sub">未选择短链</div>
    <div v-else-if="linkStats.length === 0" class="sub">暂无统计数据</div>
    <div v-else-if="!showLinkChart" class="sub">
      当前短链：<span class="mono">{{ selectedLink?.code || selectedLinkId }}</span>
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
        <tr v-for="stat in linkStats" :key="stat.day">
          <td>{{ stat.day }}</td>
          <td class="mono">{{ stat.pv }}</td>
          <td class="mono">{{ stat.uv }}</td>
        </tr>
        <tr v-if="linkStats.length === 0">
          <td colspan="3" class="sub">暂无数据</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<style scoped>
.sub {
  margin: 4px 0 0;
}

.card-head {
  gap: 12px;
}

.field {
  display: grid;
  gap: 6px;
  font-size: 14px;
  margin-bottom: 12px;
}

.linkSearch {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 5rem;
  gap: 8px;
  margin-bottom: 12px;
  max-width: 720px;
}

.linkSearch input {
  min-width: 0;
}

.loadMore {
  margin: 0 0 12px;
}

.stateButton {
  box-sizing: border-box;
  inline-size: 5rem;
  min-inline-size: 5rem;
  letter-spacing: 0;
  white-space: nowrap;
}

.error {
  margin-bottom: 8px;
}

select {
  inline-size: 100%;
  min-inline-size: 0;
  max-inline-size: 100%;
}
</style>
