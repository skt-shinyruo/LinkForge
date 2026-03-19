<script setup lang="ts">
import { computed } from "vue";
import type { LinkEditFormState } from "../../composables/useLinksPage";
import type { LinkDto } from "../../services/types";

const props = defineProps<{
  items: LinkDto[];
  loading: boolean;
  error: string | null;
  showArchived: boolean;
  keyword: string;
  editingId: number | null;
  editForm: LinkEditFormState;
  isAdmin: boolean;
  page: number;
  size: number;
  total: number;
  formatInstantLocal: (value?: string | null) => string;
  policySummary: (link: LinkDto) => string;
  statusLabel: (link: LinkDto) => string;
}>();

const emit = defineEmits<{
  refresh: [];
  setArchived: [value: boolean];
  "update:keyword": [value: string];
  previousPage: [];
  nextPage: [];
  startEdit: [link: LinkDto];
  cancelEdit: [];
  saveEdit: [];
  toggleEnabled: [link: LinkDto];
  archive: [link: LinkDto];
  restore: [link: LinkDto];
  delete: [link: LinkDto];
}>();

function onKeywordInput(event: Event) {
  emit("update:keyword", (event.target as HTMLInputElement).value);
}

const pageCount = computed(() => {
  if (props.total <= 0 || props.size <= 0) {
    return 0;
  }
  return Math.ceil(props.total / props.size);
});

const currentPageLabel = computed(() => (pageCount.value === 0 ? 0 : props.page + 1));
const hasPreviousPage = computed(() => props.page > 0);
const hasNextPage = computed(() => (props.page + 1) * props.size < props.total);
</script>

<template>
  <section class="card">
    <div class="cardHead">
      <h2>短链列表</h2>
      <div class="list-actions">
        <button class="btn secondary" :disabled="props.loading || !props.showArchived" @click="emit('setArchived', false)">
          活动
        </button>
        <button class="btn secondary" :disabled="props.loading || props.showArchived" @click="emit('setArchived', true)">
          归档
        </button>
        <input :value="props.keyword" class="keyword" placeholder="搜索短码/URL/备注" @input="onKeywordInput" />
        <button class="btn secondary" :disabled="props.loading" @click="emit('refresh')">
          {{ props.loading ? "刷新中..." : "搜索/刷新" }}
        </button>
      </div>
    </div>
    <p v-if="props.error" class="error">{{ props.error }}</p>
    <p v-if="props.loading" class="sub">加载中...</p>
    <table v-else class="table">
      <thead>
        <tr>
          <th>短码</th>
          <th>短链</th>
          <th>原始链接</th>
          <th>备注/标签</th>
          <th>有效期</th>
          <th>策略</th>
          <th>状态</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="item in props.items" :key="item.id">
          <tr>
            <td class="mono">{{ item.code }}</td>
            <td class="mono">
              <a :href="item.shortUrl" target="_blank" rel="noreferrer">{{ item.shortUrl }}</a>
            </td>
            <td class="mono">{{ item.originalUrl }}</td>
            <td>
              <div v-if="item.note" class="sub">{{ item.note }}</div>
              <div v-if="(item.tags || []).length > 0" class="tags">
                <span v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</span>
              </div>
            </td>
            <td class="mono">{{ props.formatInstantLocal(item.expiresAt) }}</td>
            <td class="mono">{{ props.policySummary(item) }}</td>
            <td>
              <span :class="item.archivedAt ? 'muted' : item.enabled ? 'ok' : 'bad'">
                {{ props.statusLabel(item) }}
              </span>
              <div v-if="item.archivedAt" class="sub">
                archivedAt: {{ props.formatInstantLocal(item.archivedAt) }}
              </div>
            </td>
            <td class="actions-col">
              <button class="btn small secondary" :disabled="!!item.archivedAt" @click="emit('startEdit', item)">
                编辑
              </button>
              <button class="btn small" :disabled="!!item.archivedAt" @click="emit('toggleEnabled', item)">
                {{ item.enabled ? "禁用" : "启用" }}
              </button>
              <button
                v-if="props.isAdmin && !item.archivedAt"
                class="btn small secondary"
                @click="emit('archive', item)"
              >
                归档
              </button>
              <button
                v-if="props.isAdmin && item.archivedAt"
                class="btn small secondary"
                @click="emit('restore', item)"
              >
                恢复
              </button>
              <button
                v-if="props.isAdmin && item.archivedAt"
                class="btn small danger"
                @click="emit('delete', item)"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="props.editingId === item.id">
            <td colspan="8">
              <div class="edit-card">
                <div class="edit-grid">
                  <label class="field span2">
                    <span class="label">原始链接</span>
                    <input v-model="props.editForm.originalUrl" placeholder="https://example.com" />
                  </label>
                  <label class="field span2">
                    <span class="label">备注</span>
                    <input v-model="props.editForm.note" placeholder="备注（可选）" />
                  </label>
                  <label class="field">
                    <span class="label">有效期（expiresAt）</span>
                    <input v-model="props.editForm.expiresAt" type="datetime-local" />
                    <span class="hint">留空表示清空有效期</span>
                  </label>
                  <label class="field checkbox">
                    <input v-model="props.editForm.enabled" type="checkbox" />
                    <span class="label">启用</span>
                  </label>
                  <label class="field span2">
                    <span class="label">标签（tags）</span>
                    <input v-model="props.editForm.tags" placeholder="例如：活动A,推广" />
                    <span class="hint">逗号或换行分隔，最多 20 个；保存会覆盖当前标签</span>
                  </label>
                  <label class="field">
                    <span class="label">跳转状态码</span>
                    <select v-model="props.editForm.redirectStatusCode">
                      <option value="">继承全局</option>
                      <option value="301">301</option>
                      <option value="302">302</option>
                    </select>
                  </label>
                  <label class="field checkbox">
                    <input v-model="props.editForm.previewEnabled" type="checkbox" />
                    <span class="label">启用预览页</span>
                  </label>
                  <label class="field">
                    <span class="label">Query 透传模式</span>
                    <select v-model="props.editForm.queryForwardMode">
                      <option value="">继承全局</option>
                      <option value="OFF">OFF</option>
                      <option value="ALLOWLIST">ALLOWLIST</option>
                      <option value="ALL">ALL</option>
                    </select>
                  </label>
                  <label class="field span2">
                    <span class="label">Query Allowlist</span>
                    <textarea v-model="props.editForm.queryForwardAllowlist" rows="2" placeholder="utm_*" />
                    <span class="hint">逗号或换行分隔，支持 utm_* 前缀通配</span>
                  </label>
                  <label class="field span2">
                    <span class="label">不可用落地页（禁用/过期）</span>
                    <input
                      v-model="props.editForm.unavailableLandingUrl"
                      placeholder="https://example.com/unavailable"
                    />
                    <span class="hint">清空会回退全局/内置 410；保存时空字符串视为清空</span>
                  </label>
                </div>
                <div class="edit-actions">
                  <button class="btn small" @click="emit('saveEdit')">保存</button>
                  <button class="btn small secondary" @click="emit('cancelEdit')">取消</button>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
    <div v-if="!props.loading" class="pagination">
      <p class="sub">
        共 {{ props.total }} 条
        <span v-if="pageCount > 0"> · 第 {{ currentPageLabel }} / {{ pageCount }} 页</span>
      </p>
      <div class="pagination-actions">
        <button class="btn small secondary" :disabled="!hasPreviousPage" @click="emit('previousPage')">上一页</button>
        <button class="btn small secondary" :disabled="!hasNextPage" @click="emit('nextPage')">下一页</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
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
  flex-wrap: wrap;
}

.list-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.keyword {
  min-width: 240px;
}

input,
select,
textarea {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font: inherit;
}

textarea {
  resize: vertical;
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

.btn.danger {
  background: #c00;
}

.btn.small {
  padding: 6px 10px;
  font-size: 12px;
}

.actions-col {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.pagination-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.sub,
.hint {
  color: #666;
  margin: 4px 0 0;
  font-size: 12px;
}

.error {
  color: #c00;
  margin: 8px 0 0;
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.tag {
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f2f2f2;
  color: #333;
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

.ok {
  color: #0a0;
}

.bad {
  color: #c00;
}

.muted {
  color: #666;
}

.edit-card {
  border: 1px solid #eee;
  border-radius: 10px;
  padding: 12px;
  background: #fafafa;
}

.edit-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.field.span2 {
  grid-column: 1 / -1;
}

.label {
  font-size: 12px;
  color: #444;
}

.edit-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
</style>
