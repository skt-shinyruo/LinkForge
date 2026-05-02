<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useApplicationDetailPage } from "../composables/useApplicationDetailPage";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";

const navigation = useAppSessionNavigation("applicationDetail");
const page = useApplicationDetailPage();
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
        <h2>{{ page.application.value?.displayName || "未知应用" }}</h2>
        <p class="sub">{{ page.application.value?.applicationKey || `ID ${page.applicationId.value}` }}</p>
      </article>
      <article class="card">
        <h2>绑定域名</h2>
        <p class="metric">{{ page.domains.value.length }}</p>
      </article>
      <article class="card">
        <h2>API Keys</h2>
        <p class="metric">{{ page.apiKeys.value.length }}</p>
      </article>
      <article class="card">
        <h2>近 7 天 PV</h2>
        <p class="metric">{{ page.recentPv.value }}</p>
      </article>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>Top Links</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
      <ul v-else class="list">
        <li v-for="item in page.topLinks.value" :key="item.linkId">
          <strong>{{ item.code || "已删除" }}</strong>
          <span class="sub">PV {{ item.pv }} / UV {{ item.uv }}</span>
        </li>
        <li v-if="page.topLinks.value.length === 0" class="sub">暂无统计</li>
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
  align-items: center;
}

.metric {
  font-size: 28px;
  font-weight: 700;
  margin: 8px 0 0;
}

.list {
  list-style: none;
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
