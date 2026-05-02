<script setup lang="ts">
import type { LinkCreateFormState } from "../../composables/useLinksPage";
import type { LinkImportResult } from "../../services/types";

const props = defineProps<{
  form: LinkCreateFormState;
  creating: boolean;
  importing: boolean;
  importFileName: string;
  importResult: LinkImportResult | null;
  isAdmin: boolean;
  error: string | null;
}>();

const emit = defineEmits<{
  create: [];
  import: [];
  export: [];
  fileChange: [file: File | null];
}>();

function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  emit("fileChange", input.files?.[0] ?? null);
}
</script>

<template>
  <section class="card">
    <h2>创建短链</h2>
    <div class="form">
      <input v-model="props.form.originalUrl" placeholder="https://example.com" />
      <input v-model="props.form.note" placeholder="备注（可选）" />
      <button class="btn" :disabled="props.creating || !props.form.originalUrl.trim()" @click="emit('create')">
        {{ props.creating ? "创建中..." : "创建" }}
      </button>
    </div>
    <details class="advanced">
      <summary>高级跳转策略（可选）</summary>
      <div class="advanced-grid">
        <label class="field">
          <span class="label">自定义短码</span>
          <input
            v-model="props.form.customCode"
            placeholder="可选：6-32 位 [0-9A-Za-z]"
            maxlength="32"
          />
        </label>
        <label class="field">
          <span class="label">有效期（expiresAt）</span>
          <input v-model="props.form.expiresAt" type="datetime-local" />
          <span class="hint">留空表示永不过期</span>
        </label>
        <label class="field checkbox">
          <input v-model="props.form.enabled" type="checkbox" />
          <span class="label">创建后立即启用</span>
        </label>
        <label class="field span2">
          <span class="label">标签（tags）</span>
          <input v-model="props.form.tags" placeholder="例如：活动A,推广" />
          <span class="hint">逗号或换行分隔，最多 20 个</span>
        </label>
        <label class="field">
          <span class="label">跳转状态码</span>
          <select v-model="props.form.redirectStatusCode">
            <option value="">使用全局默认</option>
            <option value="301">301</option>
            <option value="302">302</option>
          </select>
        </label>
        <label class="field checkbox">
          <input v-model="props.form.previewEnabled" type="checkbox" />
          <span class="label">启用预览页（确认后跳转）</span>
        </label>
        <label class="field">
          <span class="label">Query 透传模式</span>
          <select v-model="props.form.queryForwardMode">
            <option value="">继承全局</option>
            <option value="OFF">OFF</option>
            <option value="ALLOWLIST">ALLOWLIST</option>
            <option value="ALL">ALL</option>
          </select>
        </label>
        <label class="field span2">
          <span class="label">Query Allowlist</span>
          <textarea
            v-model="props.form.queryForwardAllowlist"
            placeholder="utm_*"
            rows="2"
          />
          <span class="hint">逗号或换行分隔，支持 utm_* 前缀通配</span>
        </label>
        <label class="field span2">
          <span class="label">不可用落地页（禁用/过期）</span>
          <input
            v-model="props.form.unavailableLandingUrl"
            placeholder="https://example.com/unavailable"
          />
          <span class="hint">留空表示使用全局/内置 410 页面</span>
        </label>
      </div>
    </details>
    <p v-if="props.error" class="error">{{ props.error }}</p>
  </section>

  <section v-if="props.isAdmin" class="card">
    <h2>批量导入导出（管理员）</h2>
    <div class="bulk">
      <input type="file" accept=".csv,text/csv" @change="onImportFileChange" />
      <span v-if="props.importFileName" class="sub">{{ props.importFileName }}</span>
      <button class="btn" :disabled="props.importing || !props.importFileName" @click="emit('import')">
        {{ props.importing ? "导入中..." : "导入 CSV" }}
      </button>
      <button class="btn secondary" @click="emit('export')">导出 CSV</button>
    </div>
    <p class="sub">
      CSV Header：originalUrl, code(可选), expiresAt(可选，推荐 ISO-8601 Instant，如 2026-03-16T12:34:56Z；兼容旧
      LocalDateTime，将按 UTC 解析), note(可选), tags(可选, 逗号分隔)
    </p>
    <div v-if="props.importResult" class="import-result">
      <p class="sub">导入结果：成功 {{ props.importResult.success }}，失败 {{ props.importResult.failed }}</p>
      <ul v-if="props.importResult.errors.length" class="error-list">
        <li v-for="message in props.importResult.errors" :key="message">{{ message }}</li>
      </ul>
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

.form {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
}

.advanced {
  margin-top: 12px;
}

.advanced summary {
  cursor: pointer;
  user-select: none;
}

.advanced-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
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

.hint,
.sub {
  font-size: 12px;
  color: #666;
}

.bulk {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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

.error {
  color: #c00;
  margin: 8px 0 0;
}

.import-result {
  margin-top: 8px;
}

.error-list {
  color: #c00;
  margin: 6px 0 0;
  padding-left: 20px;
}
</style>
