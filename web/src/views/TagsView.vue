<script setup lang="ts">
import { onMounted, ref } from "vue";
import AppPageShell from "../components/AppPageShell.vue";
import { useAppSessionNavigation } from "../composables/useAppSessionNavigation";
import { createTag, listTags } from "../services/tags";
import type { TagDto } from "../services/types";

const navigation = useAppSessionNavigation("tags");

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

onMounted(() => {
  void load();
});
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
      <div class="cardHead">
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
