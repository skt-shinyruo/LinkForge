<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useApiKeysPage } from "../composables/useApiKeysPage";

const page = useApiKeysPage();
void page.load();
</script>

<template>
  <AppPageShell>
    <section class="card">
      <h2>创建 API Key</h2>
      <div class="form">
        <select v-model="page.createForm.applicationId">
          <option :value="null">选择应用</option>
          <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
            {{ application.displayName }}
          </option>
        </select>
        <input v-model="page.createForm.name" placeholder="API Key name" />
        <button
          class="btn"
          :disabled="page.creating.value || !page.createForm.applicationId || !page.createForm.name.trim()"
          @click="page.create"
        >
          {{ page.creating.value ? "创建中..." : "创建" }}
        </button>
      </div>
      <p v-if="page.latestCreated.value" class="sub">
        最新 Key：{{ page.latestCreated.value.apiKey }}
      </p>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>API Key 列表</h2>
        <select
          :value="page.selectedApplicationId.value ?? ''"
          @change="page.setSelectedApplicationId(($event.target as HTMLSelectElement).value ? Number(($event.target as HTMLSelectElement).value) : null)"
        >
          <option value="">全部应用</option>
          <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
            {{ application.displayName }}
          </option>
        </select>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Application</th>
            <th>Status</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in page.apiKeys.value" :key="key.id">
            <td>{{ key.name }}</td>
            <td>{{ key.applicationId ?? "-" }}</td>
            <td>{{ key.status }}</td>
            <td class="actions">
              <button class="btn secondary" :disabled="page.actingId.value === key.id" @click="page.rotate(key.id)">
                轮换
              </button>
              <button
                v-if="key.status === 'active'"
                class="btn secondary"
                :disabled="page.actingId.value === key.id"
                @click="page.disable(key.id)"
              >
                禁用
              </button>
              <button
                v-else
                class="btn secondary"
                :disabled="page.actingId.value === key.id"
                @click="page.enable(key.id)"
              >
                启用
              </button>
            </td>
          </tr>
          <tr v-if="page.apiKeys.value.length === 0">
            <td colspan="4" class="sub">暂无 API Key</td>
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

.table {
  margin-top: 12px;
}
</style>
