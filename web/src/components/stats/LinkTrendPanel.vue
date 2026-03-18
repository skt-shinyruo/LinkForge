<script setup lang="ts">
import { defineAsyncComponent } from "vue";

const LineChart = defineAsyncComponent(() => import("../LineChart.vue"));

defineProps<{
  links: { id: number; code: string; originalUrl: string }[];
  selectedLinkId: number | null;
  selectedLink: { id: number; code: string; originalUrl: string } | null;
  linkStats: { day: string; pv: number; uv: number }[];
  showLinkChart: boolean;
  linkChartLabels: string[];
  linkChartSeries: { name: string; data: number[] }[];
}>();

defineEmits<{
  toggle: [];
  selectLink: [value: number | null];
}>();
</script>

<template>
  <section class="card">
    <div class="cardHead">
      <h2>单短链趋势（PV / UV）</h2>
      <button class="btn small secondary" :disabled="!selectedLinkId || linkStats.length === 0" @click="$emit('toggle')">
        {{ showLinkChart ? "收起图表" : "显示图表" }}
      </button>
    </div>
    <label class="field">
      选择短链
      <select :value="selectedLinkId ?? ''" @change="$emit('selectLink', Number(($event.target as HTMLSelectElement).value) || null)">
        <option v-for="link in links" :key="link.id" :value="link.id">{{ link.code }} - {{ link.originalUrl }}</option>
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
