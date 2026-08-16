<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useAuditPage } from "../composables/useAuditPage";

const page = useAuditPage();
void page.load();
</script>

<template>
  <AppPageShell>
    <section class="card">
      <div class="card-head">
        <h2>审计日志</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
      <table class="table">
        <thead>
          <tr>
            <th>Time</th>
            <th>Actor</th>
            <th>Action</th>
            <th>Resource</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in page.logs.value" :key="log.id">
            <td>{{ log.createdAt }}</td>
            <td>{{ log.actorEmail }}</td>
            <td>{{ log.actionType }}</td>
            <td>{{ log.resourceType }} / {{ log.resourceId }}</td>
          </tr>
          <tr v-if="page.logs.value.length === 0">
            <td colspan="4" class="sub">暂无审计记录</td>
          </tr>
        </tbody>
      </table>
      <div v-if="page.hasMore.value" class="load-more">
        <button class="btn secondary" :disabled="page.loading.value" @click="page.loadMore">
          {{ page.loading.value ? "加载中..." : "加载更多" }}
        </button>
      </div>
    </section>
  </AppPageShell>
</template>

<style scoped>
.table {
  margin-top: 12px;
}
</style>
