<script setup lang="ts">
defineProps<{
  rangeDays: 7 | 30;
  range: { from: string; to: string };
  loading: boolean;
  error: string | null;
}>();

defineEmits<{
  setRange: [days: 7 | 30];
  refresh: [];
}>();
</script>

<template>
  <section class="card">
    <div class="toolbar">
      <div class="range">
        <span class="label">时间范围</span>
        <button class="btn small" :class="rangeDays === 7 ? 'active' : ''" @click="$emit('setRange', 7)">
          近 7 天
        </button>
        <button class="btn small" :class="rangeDays === 30 ? 'active' : ''" @click="$emit('setRange', 30)">
          近 30 天
        </button>
        <span class="sub">{{ range.from }} ~ {{ range.to }}（UTC）</span>
      </div>
      <button class="btn secondary" :disabled="loading" @click="$emit('refresh')">
        {{ loading ? "刷新中..." : "刷新" }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
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
</style>
