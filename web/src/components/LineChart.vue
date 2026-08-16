<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { LineChart as ELineChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import type { EChartsType } from "echarts/core";

// 仅注册当前组件需要的图表与组件，避免引入完整 ECharts 包。
echarts.use([ELineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

type LineSeries = {
  name: string;
  data: number[];
};

const props = defineProps<{
  labels: string[];
  series: LineSeries[];
}>();

const rootEl = ref<HTMLDivElement | null>(null);
let chart: EChartsType | null = null;

function render() {
  if (!chart) return;
  chart.setOption(
    {
      tooltip: { trigger: "axis" },
      legend: { top: 0 },
      grid: { left: 42, right: 18, top: 34, bottom: 34 },
      xAxis: {
        type: "category",
        data: props.labels,
        axisLabel: { rotate: props.labels.length > 15 ? 45 : 0 },
      },
      yAxis: { type: "value" },
      series: props.series.map((s) => ({
        type: "line",
        name: s.name,
        data: s.data,
        smooth: true,
        symbol: "circle",
        symbolSize: 6,
      })),
    },
    { notMerge: true },
  );
  chart.resize();
}

function onResize() {
  chart?.resize();
}

onMounted(() => {
  if (!rootEl.value) return;
  chart = echarts.init(rootEl.value);
  render();
  window.addEventListener("resize", onResize);
});

watch(
  () => [props.labels, props.series],
  () => render(),
  { deep: true },
);

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
  chart?.dispose();
  chart = null;
});
</script>

<template>
  <div ref="rootEl" class="chart"></div>
</template>

<style scoped>
.chart {
  height: 320px;
  width: 100%;
}
</style>
