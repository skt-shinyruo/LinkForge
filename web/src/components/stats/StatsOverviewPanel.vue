<script setup lang="ts">
import { defineAsyncComponent } from "vue";

const LineChart = defineAsyncComponent(() => import("../LineChart.vue"));

defineProps<{
  overviewStats: { day: string; pv: number; uv: number }[];
  overviewChartLabels: string[];
  overviewChartSeries: { name: string; data: number[] }[];
  showOverviewChart: boolean;
}>();

defineEmits<{
  toggle: [];
}>();
</script>

<template>
  <section class="card">
    <div class="card-head">
      <h2>租户趋势（PV / UV）</h2>
      <button class="btn small secondary" :disabled="overviewStats.length === 0" @click="$emit('toggle')">
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
</template>

<style scoped>
.sub {
  margin: 4px 0 0;
}

.card-head {
  gap: 12px;
}
</style>
