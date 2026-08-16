<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { useApprovalsPage } from "../composables/useApprovalsPage";

const navigation = useAppSessionNavigation("approvals");
const page = useApprovalsPage();
</script>

<template>
  <AppPageShell
    :title="navigation.title"
    :user-email="navigation.userEmail.value"
    :nav-items="navigation.navItems"
    @navigate="navigation.navigate"
    @logout="navigation.logout"
  >
    <section class="card">
      <div class="card-head">
        <h2>审批请求</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Operation</th>
            <th>Application</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="approval in page.approvals.value" :key="approval.id">
            <td class="mono">{{ approval.id }}</td>
            <td>{{ approval.operationType }}</td>
            <td>{{ approval.targetApplicationId ?? "-" }}</td>
            <td>{{ approval.status }}</td>
            <td class="actions">
              <input
                v-if="approval.status === 'PENDING_APPROVAL'"
                :value="page.decisionReasons[approval.id] ?? ''"
                placeholder="审批原因（可选）"
                @input="page.setDecisionReason(approval.id, ($event.target as HTMLInputElement).value)"
              />
              <button
                v-if="approval.status === 'PENDING_APPROVAL'"
                class="btn"
                :disabled="page.actingId.value === approval.id"
                @click="page.approve(approval.id)"
              >
                批准
              </button>
            </td>
          </tr>
          <tr v-if="page.approvals.value.length === 0">
            <td colspan="5" class="sub">暂无审批</td>
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
.card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-head,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-head {
  justify-content: space-between;
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

.table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
}

.table th,
.table td {
  border-top: 1px solid #eee;
  padding: 10px 8px;
  text-align: left;
}

input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New",
    monospace;
  font-size: 12px;
}

.sub {
  color: #666;
}

.error {
  color: #c00;
}

.load-more {
  display: flex;
  justify-content: center;
  margin-top: 12px;
}
</style>
