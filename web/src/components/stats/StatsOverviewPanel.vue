<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from "vue";

const LineChart = defineAsyncComponent(() => import("../LineChart.vue"));

const props = defineProps<{
  overviewStats: { day: string; pv: number; uv: number }[];
}>();

const showChart = ref(false);
const labels = computed(() => props.overviewStats.map((stat) => stat.day));
const series = computed(() => [
  { name: "PV", data: props.overviewStats.map((stat) => stat.pv) },
  { name: "UV", data: props.overviewStats.map((stat) => stat.uv) },
]);
</script>

<template>
  <section class="card">
    <div class="card-head">
      <h2>租户趋势（PV / UV）</h2>
      <button class="btn small secondary" :disabled="overviewStats.length === 0" @click="showChart = !showChart">
        {{ showChart ? "收起图表" : "显示图表" }}
      </button>
    </div>
    <div v-if="overviewStats.length === 0" class="sub">暂无数据（请先访问短链，并等待/触发聚合落库）。</div>
    <div v-else-if="!showChart" class="sub">
      图表已拆分为按需加载，点击“显示图表”后再加载图表组件。
    </div>
    <Suspense v-else>
      <template #default>
        <LineChart :labels="labels" :series="series" />
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
