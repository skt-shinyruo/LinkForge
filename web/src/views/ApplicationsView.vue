<script setup lang="ts">
import { RouterLink } from "vue-router";
import AppPageShell from "../components/AppPageShell.vue";
import { useApplicationsPage } from "../composables/useApplicationsPage";

const page = useApplicationsPage();
void page.load();
</script>

<template>
  <AppPageShell>
    <section class="card">
      <h2>创建应用</h2>
      <div class="form">
        <input v-model="page.createForm.applicationKey" placeholder="applicationKey" />
        <input v-model="page.createForm.displayName" placeholder="displayName" />
        <button
          class="btn"
          :disabled="page.creating.value || !page.createForm.applicationKey.trim() || !page.createForm.displayName.trim()"
          @click="page.create"
        >
          {{ page.creating.value ? "创建中..." : "创建应用" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>应用列表</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Key</th>
            <th>名称</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="application in page.applications.value" :key="application.id">
            <td class="mono">{{ application.id }}</td>
            <td>{{ application.applicationKey }}</td>
            <td>{{ application.displayName }}</td>
            <td>
              <RouterLink :to="`/applications/${application.id}`">详情</RouterLink>
            </td>
          </tr>
          <tr v-if="page.applications.value.length === 0">
            <td colspan="4" class="sub">暂无应用</td>
          </tr>
        </tbody>
      </table>
    </section>
  </AppPageShell>
</template>

<style scoped>
.form {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

input {
  flex: 1;
  min-width: 180px;
}

.table {
  margin-top: 12px;
}

.error {
  margin-top: 8px;
}
</style>
