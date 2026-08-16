<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useTenantOverviewPage } from "../composables/useTenantOverviewPage";

const page = useTenantOverviewPage();
void page.load();
</script>

<template>
  <AppPageShell>
    <section class="summary-grid">
      <article class="card">
        <h2>应用</h2>
        <p class="metric">{{ page.applications.value.length }}</p>
      </article>
      <article class="card">
        <h2>域名</h2>
        <p class="metric">{{ page.domains.value.length }}</p>
      </article>
      <article class="card">
        <h2>待处理审批</h2>
        <p class="metric">{{ page.pendingApprovalCount.value }}</p>
      </article>
      <article class="card">
        <h2>最近审计</h2>
        <p class="metric">{{ page.auditLogs.value.length }}</p>
      </article>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>最近应用</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
      <ul v-else class="list">
        <li v-for="application in page.applications.value.slice(0, 5)" :key="application.id">
          <strong>{{ application.displayName }}</strong>
          <span class="sub">{{ application.applicationKey }}</span>
        </li>
        <li v-if="page.applications.value.length === 0" class="sub">暂无应用</li>
      </ul>
    </section>
  </AppPageShell>
</template>

<style scoped>
.sub {
  display: block;
}
</style>
