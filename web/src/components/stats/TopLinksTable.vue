<script setup lang="ts">
defineProps<{
  topLinks: {
    linkId: number;
    code: string | null;
    shortUrl: string | null;
    originalUrl: string | null;
    pv: number;
    uv: number;
  }[];
  topSortBy: "pv" | "uv";
}>();

defineEmits<{
  setTopSortBy: [value: "pv" | "uv"];
  copyShort: [shortUrl: string | null];
}>();
</script>

<template>
  <section class="card">
    <div class="card-head">
      <h2>Top 链接（按 {{ topSortBy.toUpperCase() }}）</h2>
      <div class="range">
        <span class="label">排序</span>
        <button class="btn small" :class="topSortBy === 'pv' ? 'active' : ''" @click="$emit('setTopSortBy', 'pv')">
          按 PV
        </button>
        <button class="btn small" :class="topSortBy === 'uv' ? 'active' : ''" @click="$emit('setTopSortBy', 'uv')">
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
        <tr v-for="(item, idx) in topLinks" :key="item.linkId">
          <td class="mono">{{ idx + 1 }}</td>
          <td class="mono">
            <span v-if="item.code">{{ item.code }}</span>
            <span v-else class="sub">已删除</span>
          </td>
          <td class="mono">
            <a v-if="item.shortUrl" :href="item.shortUrl" target="_blank" rel="noreferrer">{{ item.shortUrl }}</a>
            <span v-else class="sub">已删除</span>
          </td>
          <td class="mono">
            <span v-if="item.originalUrl">{{ item.originalUrl }}</span>
            <span v-else class="sub">-</span>
          </td>
          <td class="mono">{{ item.pv }}</td>
          <td class="mono">{{ item.uv }}</td>
          <td>
            <button class="btn small secondary" :disabled="!item.shortUrl" @click="$emit('copyShort', item.shortUrl)">
              复制
            </button>
          </td>
        </tr>
        <tr v-if="topLinks.length === 0">
          <td colspan="7" class="sub">暂无数据</td>
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

.range {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.label {
  font-size: 14px;
  color: #111;
}
</style>
