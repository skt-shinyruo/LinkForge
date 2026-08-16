<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useApprovalsPage } from "../composables/useApprovalsPage";

const page = useApprovalsPage();
void page.load();
</script>

<template>
  <AppPageShell>
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
.table {
  margin-top: 12px;
}
</style>
