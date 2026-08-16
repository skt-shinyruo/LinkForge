<script setup lang="ts">
import { ref } from "vue";
import AppPageShell from "../components/AppPageShell.vue";
import { createTag, listTags } from "../services/tags";
import type { TagDto } from "../services/types";

const loading = ref(false);
const error = ref<string | null>(null);
const tags = ref<TagDto[]>([]);

const creating = ref(false);
const newName = ref("");

function getErrorMessage(caught: unknown, fallbackMessage: string) {
  return caught instanceof Error ? caught.message : fallbackMessage;
}

async function load() {
  loading.value = true;
  error.value = null;
  try {
    tags.value = await listTags();
  } catch (caught) {
    error.value = getErrorMessage(caught, "加载失败");
  } finally {
    loading.value = false;
  }
}

async function submitCreateTag() {
  const name = newName.value.trim();
  if (!name) {
    return;
  }
  creating.value = true;
  error.value = null;
  try {
    await createTag({ name });
    newName.value = "";
    await load();
  } catch (caught) {
    error.value = getErrorMessage(caught, "创建失败");
  } finally {
    creating.value = false;
  }
}

void load();
</script>

<template>
  <AppPageShell>
    <section class="card">
      <h2>创建标签</h2>
      <div class="form">
        <input v-model="newName" placeholder="例如：推广/活动A" maxlength="64" />
        <button class="btn" :disabled="creating || !newName.trim()" @click="submitCreateTag">
          {{ creating ? "创建中..." : "创建" }}
        </button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section class="card">
      <div class="card-head">
        <h2>标签列表</h2>
        <button class="btn secondary" :disabled="loading" @click="load">
          {{ loading ? "刷新中..." : "刷新" }}
        </button>
      </div>
      <p v-if="loading" class="sub">加载中...</p>
      <table v-else class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tag in tags" :key="tag.id">
            <td class="mono">{{ tag.id }}</td>
            <td>{{ tag.name }}</td>
          </tr>
          <tr v-if="tags.length === 0">
            <td colspan="2" class="sub">暂无标签</td>
          </tr>
        </tbody>
      </table>
    </section>
  </AppPageShell>
</template>

<style scoped>
.sub {
  margin: 4px 0 0;
}

.form {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.error {
  margin: 8px 0 0;
}
</style>
