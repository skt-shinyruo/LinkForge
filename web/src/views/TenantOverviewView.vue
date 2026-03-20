<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { useTenantOverviewPage } from "../composables/useTenantOverviewPage";

const navigation = useAppSessionNavigation("overview");
const page = useTenantOverviewPage();
</script>

<template>
  <AppPageShell
    :title="navigation.title"
    :user-email="navigation.userEmail.value"
    :nav-items="navigation.navItems"
    @navigate="navigation.navigate"
    @logout="navigation.logout"
  >
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
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.metric {
  font-size: 28px;
  font-weight: 700;
  margin: 8px 0 0;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
}

.sub {
  color: #666;
  display: block;
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

.error {
  color: #c00;
}
</style>
