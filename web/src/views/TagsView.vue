<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiFetch } from "../services/http";
import type { ApiResponse, TagDto } from "../services/types";
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";

const auth = useAuthStore();
const router = useRouter();

const loading = ref(false);
const error = ref<string | null>(null);
const tags = ref<TagDto[]>([]);

const creating = ref(false);
const newName = ref("");

async function load() {
  loading.value = true;
  error.value = null;
  try {
    const r: ApiResponse<TagDto[]> = await apiFetch<TagDto[]>("/api/v1/tags");
    if (r.code !== 0) {
      throw new Error(r.message || "加载标签失败");
    }
    tags.value = r.data || [];
  } catch (e: any) {
    error.value = e?.message || "加载失败";
  } finally {
    loading.value = false;
  }
}

async function createTag() {
  const name = newName.value.trim();
  if (!name) return;
  creating.value = true;
  error.value = null;
  try {
    const r: ApiResponse<TagDto> = await apiFetch<TagDto>("/api/v1/tags", {
      method: "POST",
      body: JSON.stringify({ name }),
    });
    if (r.code !== 0) {
      throw new Error(r.message || "创建失败");
    }
    newName.value = "";
    await load();
  } catch (e: any) {
    error.value = e?.message || "创建失败";
  } finally {
    creating.value = false;
  }
}

function goLinks() {
  router.push("/links");
}

function goStats() {
  router.push("/stats");
}

function logout() {
  auth.logout();
  router.replace("/login");
}

onMounted(load);
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>标签管理</h1>
        <p class="sub">当前用户：{{ auth.email }}</p>
      </div>
      <div class="actions">
        <button class="btn secondary" @click="goLinks">短链</button>
        <button class="btn secondary" @click="goStats">统计</button>
        <button class="btn secondary" @click="logout">退出</button>
      </div>
    </header>

    <section class="card">
      <h2>创建标签</h2>
      <div class="form">
        <input v-model="newName" placeholder="例如：推广/活动A" maxlength="64" />
        <button class="btn" :disabled="creating || !newName.trim()" @click="createTag">
          {{ creating ? "创建中..." : "创建" }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <div class="cardHead">
        <h2>标签列表</h2>
        <button class="btn secondary" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="loading" class="sub">加载中...</p>
      <table class="table" v-else>
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tags" :key="t.id">
            <td class="mono">{{ t.id }}</td>
            <td>{{ t.name }}</td>
          </tr>
          <tr v-if="tags.length === 0">
            <td colspan="2" class="sub">暂无标签</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 24px auto;
  padding: 0 16px;
}
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
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
.cardHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.form {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
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
  margin: 8px 0 0;
}
.table {
  width: 100%;
  border-collapse: collapse;
}
.table th,
.table td {
  border-top: 1px solid #eee;
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New",
    monospace;
  font-size: 12px;
}
</style>

