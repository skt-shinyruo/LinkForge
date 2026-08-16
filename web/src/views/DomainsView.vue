<script setup lang="ts">
import AppPageShell from "../components/AppPageShell.vue";
import { useDomainsPage } from "../composables/useDomainsPage";

const page = useDomainsPage();
void page.load();
</script>

<template>
  <AppPageShell>
    <section class="card">
      <h2>创建域名</h2>
      <div class="form">
        <input v-model="page.createForm.hostname" placeholder="example.test" />
        <select v-model="page.createForm.applicationId">
          <option :value="null">租户共享域名</option>
          <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
            {{ application.displayName }}
          </option>
        </select>
        <button class="btn" :disabled="page.creating.value || !page.createForm.hostname.trim()" @click="page.create">
          {{ page.creating.value ? "创建中..." : "创建域名" }}
        </button>
      </div>
    </section>

    <section class="card">
      <h2>共享域名授权</h2>
      <div class="form">
        <select v-model="page.authorizationForm.applicationId">
          <option :value="null">选择应用</option>
          <option v-for="application in page.applications.value" :key="application.id" :value="application.id">
            {{ application.displayName }}
          </option>
        </select>
        <select v-model="page.authorizationForm.domainId">
          <option :value="null">选择共享域名</option>
          <option v-for="domain in page.tenantSharedDomains.value" :key="domain.id" :value="domain.id">
            {{ domain.hostname }}
          </option>
        </select>
        <button
          class="btn secondary"
          :disabled="page.authorizing.value || !page.authorizationForm.applicationId || !page.authorizationForm.domainId"
          @click="page.authorize"
        >
          {{ page.authorizing.value ? "授权中..." : "授权" }}
        </button>
      </div>
      <p v-if="page.error.value" class="error">{{ page.error.value }}</p>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>域名列表</h2>
        <button class="btn secondary" :disabled="page.loading.value" @click="page.load">
          {{ page.loading.value ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>Hostname</th>
            <th>Scope</th>
            <th>Application</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="domain in page.domains.value" :key="domain.id">
            <td>{{ domain.hostname }}</td>
            <td>{{ domain.scope }}</td>
            <td>{{ domain.applicationId ?? "-" }}</td>
          </tr>
          <tr v-if="page.domains.value.length === 0">
            <td colspan="3" class="sub">暂无域名</td>
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
