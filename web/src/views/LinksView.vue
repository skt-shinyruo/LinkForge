<script setup lang="ts">
import { onMounted, ref } from "vue";
import { apiFetch, authFetch } from "../services/http";
import type { ApiResponse, LinkDto, PageResponse } from "../services/types";
import { useAuthStore } from "../stores/auth";
import { useRouter } from "vue-router";

const auth = useAuthStore();
const router = useRouter();

const loading = ref(false);
const error = ref<string | null>(null);
const items = ref<LinkDto[]>([]);

const showArchived = ref(false);
const keyword = ref("");

const newUrl = ref("");
const newNote = ref("");
const newCustomCode = ref("");
const newExpiresAt = ref("");
const newTags = ref("");
const newEnabled = ref(true);
const creating = ref(false);

// 高级跳转策略（创建）
const newRedirectStatusCode = ref<string>("");
const newPreviewEnabled = ref(false);
const newUnavailableLandingUrl = ref("");
const newQueryForwardMode = ref<string>("");
const newQueryForwardAllowlist = ref("");

const importing = ref(false);
const importFile = ref<File | null>(null);

// 高级跳转策略（编辑）
const editingId = ref<number | null>(null);
const editRedirectStatusCode = ref<string>("");
const editPreviewEnabled = ref(false);
const editUnavailableLandingUrl = ref("");
const editQueryForwardMode = ref<string>("");
const editQueryForwardAllowlist = ref("");
const editOriginalUrl = ref("");
const editNote = ref("");
const editExpiresAt = ref("");
const editTags = ref("");
const editEnabled = ref(true);

function buildListUrl(): string {
  const params = new URLSearchParams();
  params.set("page", "0");
  params.set("size", "50");
  params.set("archived", showArchived.value ? "true" : "false");
  const k = keyword.value.trim();
  if (k) {
    params.set("keyword", k);
  }
  return `/api/v1/links?${params.toString()}`;
}

function onImportFileChange(e: Event) {
  const el = e.target as HTMLInputElement;
  importFile.value = el.files?.[0] || null;
}

async function load() {
  loading.value = true;
  error.value = null;
  try {
    const r: ApiResponse<PageResponse<LinkDto>> = await apiFetch<PageResponse<LinkDto>>(
      buildListUrl(),
    );
    if (r.code !== 0) {
      throw new Error(r.message || "加载失败");
    }
    items.value = r.data?.items || [];
  } catch (e: any) {
    error.value = e?.message || "加载失败";
  } finally {
    loading.value = false;
  }
}

async function createLink() {
  creating.value = true;
  error.value = null;
  try {
    const allowlist = parseAllowlist(newQueryForwardAllowlist.value);
    const tags = parseTags(newTags.value);
    const expiresAt = dateTimeLocalToInstantString(newExpiresAt.value);
    const r: ApiResponse<LinkDto> = await apiFetch<LinkDto>("/api/v1/links", {
      method: "POST",
      body: JSON.stringify({
        originalUrl: newUrl.value,
        note: newNote.value || undefined,
        enabled: newEnabled.value,
        customCode: newCustomCode.value.trim() || undefined,
        expiresAt,
        tags: tags.length > 0 ? tags : undefined,
        redirectStatusCode: newRedirectStatusCode.value
          ? Number(newRedirectStatusCode.value)
          : undefined,
        previewEnabled: newPreviewEnabled.value,
        unavailableLandingUrl: newUnavailableLandingUrl.value.trim() || undefined,
        queryForwardMode: newQueryForwardMode.value || undefined,
        queryForwardAllowlist: allowlist && allowlist.length > 0 ? allowlist : undefined,
      }),
    });
    if (r.code !== 0 || !r.data) {
      throw new Error(r.message || "创建失败");
    }
    showArchived.value = false; // 创建的链接默认在活动列表
    newUrl.value = "";
    newNote.value = "";
    newCustomCode.value = "";
    newExpiresAt.value = "";
    newTags.value = "";
    newEnabled.value = true;
    newRedirectStatusCode.value = "";
    newPreviewEnabled.value = false;
    newUnavailableLandingUrl.value = "";
    newQueryForwardMode.value = "";
    newQueryForwardAllowlist.value = "";
    await load();
  } catch (e: any) {
    error.value = e?.message || "创建失败";
  } finally {
    creating.value = false;
  }
}

async function toggleEnabled(link: LinkDto) {
  error.value = null;
  try {
    if (link.archivedAt) {
      throw new Error("短链已归档，请先恢复后再启用/禁用");
    }
    const r: ApiResponse<LinkDto> = await apiFetch<LinkDto>(`/api/v1/links/${link.id}`, {
      method: "PUT",
      body: JSON.stringify({ enabled: !link.enabled }),
    });
    if (r.code !== 0) {
      throw new Error(r.message || "更新失败");
    }
    await load();
  } catch (e: any) {
    error.value = e?.message || "更新失败";
  }
}

function startEdit(link: LinkDto) {
  if (link.archivedAt) {
    error.value = "短链已归档，请先恢复后再编辑";
    return;
  }
  editingId.value = link.id;
  editOriginalUrl.value = link.originalUrl || "";
  editNote.value = link.note || "";
  editExpiresAt.value = instantStringToDateTimeLocalInput(link.expiresAt);
  editTags.value = (link.tags || []).join(",");
  editEnabled.value = !!link.enabled;
  editRedirectStatusCode.value =
    link.redirectStatusCode === 301 || link.redirectStatusCode === 302
      ? String(link.redirectStatusCode)
      : "";
  editPreviewEnabled.value = !!link.previewEnabled;
  editUnavailableLandingUrl.value = link.unavailableLandingUrl || "";
  editQueryForwardMode.value = link.queryForwardMode || "";
  editQueryForwardAllowlist.value = (link.queryForwardAllowlist || []).join(",");
}

function cancelEdit() {
  editingId.value = null;
}

async function saveEdit() {
  if (!editingId.value) {
    return;
  }
  error.value = null;
  try {
    const originalUrl = editOriginalUrl.value.trim();
    if (!originalUrl) {
      throw new Error("原始链接不能为空");
    }

    const allowlist = parseAllowlist(editQueryForwardAllowlist.value) || [];
    const expiresAt = dateTimeLocalToInstantString(editExpiresAt.value);
    const tags = parseTags(editTags.value);
    const payload: any = {
      originalUrl,
      note: editNote.value,
      enabled: editEnabled.value,
      previewEnabled: editPreviewEnabled.value,
      unavailableLandingUrl: editUnavailableLandingUrl.value, // 空字符串视为清空
      queryForwardAllowlist: allowlist,
      tags,
    };

    if (expiresAt) {
      payload.expiresAt = expiresAt;
    } else {
      payload.clearExpiresAt = true;
    }

    if (editRedirectStatusCode.value) {
      payload.redirectStatusCode = Number(editRedirectStatusCode.value);
    } else {
      payload.clearRedirectStatusCode = true;
    }

    if (editQueryForwardMode.value) {
      payload.queryForwardMode = editQueryForwardMode.value;
    } else {
      payload.clearQueryForwardMode = true;
    }

    const r: ApiResponse<LinkDto> = await apiFetch<LinkDto>(`/api/v1/links/${editingId.value}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    if (r.code !== 0) {
      throw new Error(r.message || "更新失败");
    }
    editingId.value = null;
    await load();
  } catch (e: any) {
    error.value = e?.message || "更新失败";
  }
}

function parseAllowlist(raw: string): string[] | null {
  const s = raw?.trim();
  if (!s) {
    return null;
  }
  // 支持逗号/换行分隔
  const parts = s
    .split(/[\n,]+/g)
    .map((x) => x.trim())
    .filter((x) => !!x);
  const uniq = Array.from(new Set(parts));
  return uniq;
}

function parseTags(raw: string): string[] {
  const s = raw?.trim();
  if (!s) {
    return [];
  }
  const parts = s
    .split(/[\n,]+/g)
    .map((x) => x.trim())
    .filter((x) => !!x);
  return Array.from(new Set(parts)).slice(0, 20);
}

function dateTimeLocalToInstantString(raw: string): string | undefined {
  const s = raw?.trim();
  if (!s) {
    return undefined;
  }
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) {
    return undefined;
  }
  return d.toISOString();
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

function instantStringToDateTimeLocalInput(v?: string | null): string {
  if (!v) {
    return "";
  }
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) {
    return "";
  }
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

function formatInstantLocal(v?: string | null): string {
  if (!v) {
    return "-";
  }
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) {
    return String(v);
  }
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

function policySummary(link: LinkDto): string {
  const sc =
    link.redirectStatusCode === 301 || link.redirectStatusCode === 302
      ? String(link.redirectStatusCode)
      : "默认";
  const preview = link.previewEnabled ? "Preview" : "";
  const q = link.queryForwardMode ? link.queryForwardMode : "默认";
  return [sc, q, preview].filter((x) => !!x).join(" / ");
}

function statusLabel(link: LinkDto): string {
  if (link.archivedAt) {
    return "已归档";
  }
  return link.enabled ? "启用" : "禁用";
}

async function archiveLink(link: LinkDto) {
  error.value = null;
  try {
    const r: ApiResponse<LinkDto> = await apiFetch<LinkDto>(`/api/v1/links/${link.id}/archive`, {
      method: "POST",
    });
    if (r.code !== 0) {
      throw new Error(r.message || "归档失败");
    }
    await load();
  } catch (e: any) {
    error.value = e?.message || "归档失败";
  }
}

async function restoreLink(link: LinkDto) {
  error.value = null;
  try {
    const r: ApiResponse<LinkDto> = await apiFetch<LinkDto>(`/api/v1/links/${link.id}/restore`, {
      method: "POST",
    });
    if (r.code !== 0) {
      throw new Error(r.message || "恢复失败");
    }
    await load();
  } catch (e: any) {
    error.value = e?.message || "恢复失败";
  }
}

async function deleteLink(link: LinkDto) {
  error.value = null;
  try {
    if (!link.archivedAt) {
      throw new Error("删除前请先归档");
    }
    const ok = window.confirm(`确认删除短链 ${link.code}？该操作不可恢复。`);
    if (!ok) {
      return;
    }
    const r: ApiResponse<void> = await apiFetch<void>(`/api/v1/links/${link.id}`, {
      method: "DELETE",
    });
    if (r.code !== 0) {
      throw new Error(r.message || "删除失败");
    }
    await load();
  } catch (e: any) {
    error.value = e?.message || "删除失败";
  }
}

function setArchived(v: boolean) {
  showArchived.value = v;
  load();
}

async function importCsv() {
  if (!importFile.value) {
    return;
  }
  importing.value = true;
  error.value = null;
  try {
    const fd = new FormData();
    fd.append("file", importFile.value);
    const resp = await authFetch("/api/v1/links/import", {
      method: "POST",
      body: fd,
    });
    const text = await resp.text();
    const json = text ? (JSON.parse(text) as ApiResponse<any>) : ({} as ApiResponse<any>);
    if (!resp.ok || json.code !== 0) {
      throw new Error(json.message || `导入失败（HTTP ${resp.status}）`);
    }
    importFile.value = null;
    await load();
  } catch (e: any) {
    error.value = e?.message || "导入失败";
  } finally {
    importing.value = false;
  }
}

async function exportCsv() {
  error.value = null;
  try {
    const resp = await authFetch("/api/v1/links/export?page=0&size=1000");
    if (!resp.ok) {
      throw new Error(`导出失败（HTTP ${resp.status}）`);
    }
    const blob = await resp.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "links.csv";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e: any) {
    error.value = e?.message || "导出失败";
  }
}

function goStats() {
  router.push("/stats");
}

function goTags() {
  router.push("/tags");
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
        <h1>短链管理</h1>
        <p class="sub">当前用户：{{ auth.email }}</p>
      </div>
      <div class="actions">
        <button class="btn secondary" @click="goTags">标签</button>
        <button class="btn" @click="goStats">统计</button>
        <button class="btn secondary" @click="logout">退出</button>
      </div>
    </header>

    <section class="card">
      <h2>创建短链</h2>
      <div class="form">
        <input v-model="newUrl" placeholder="https://example.com" />
        <input v-model="newNote" placeholder="备注（可选）" />
        <button class="btn" :disabled="creating || !newUrl" @click="createLink">
          {{ creating ? "创建中..." : "创建" }}
        </button>
      </div>
      <details class="advanced">
        <summary>高级跳转策略（可选）</summary>
        <div class="advanced-grid">
          <label class="field">
            <span class="label">自定义短码</span>
            <input v-model="newCustomCode" placeholder="可选：6-32 位 [0-9A-Za-z]" maxlength="32" />
          </label>
          <label class="field">
            <span class="label">有效期（expiresAt）</span>
            <input v-model="newExpiresAt" type="datetime-local" />
            <span class="hint">留空表示永不过期</span>
          </label>
          <label class="field checkbox">
            <input type="checkbox" v-model="newEnabled" />
            <span class="label">创建后立即启用</span>
          </label>
          <label class="field span2">
            <span class="label">标签（tags）</span>
            <input v-model="newTags" placeholder="例如：活动A,推广" />
            <span class="hint">逗号或换行分隔，最多 20 个</span>
          </label>
          <label class="field">
            <span class="label">跳转状态码</span>
            <select v-model="newRedirectStatusCode">
              <option value="">使用全局默认</option>
              <option value="301">301</option>
              <option value="302">302</option>
            </select>
          </label>
          <label class="field checkbox">
            <input type="checkbox" v-model="newPreviewEnabled" />
            <span class="label">启用预览页（确认后跳转）</span>
          </label>
          <label class="field">
            <span class="label">Query 透传模式</span>
            <select v-model="newQueryForwardMode">
              <option value="">继承全局</option>
              <option value="OFF">OFF</option>
              <option value="ALLOWLIST">ALLOWLIST</option>
              <option value="ALL">ALL</option>
            </select>
          </label>
          <label class="field span2">
            <span class="label">Query Allowlist</span>
            <textarea
              v-model="newQueryForwardAllowlist"
              placeholder="utm_*"
              rows="2"
            />
            <span class="hint">逗号或换行分隔，支持 utm_* 前缀通配</span>
          </label>
          <label class="field span2">
            <span class="label">不可用落地页（禁用/过期）</span>
            <input v-model="newUnavailableLandingUrl" placeholder="https://example.com/unavailable" />
            <span class="hint">留空表示使用全局/内置 410 页面</span>
          </label>
        </div>
      </details>
      <p v-if="error" class="error">{{ error }}</p>
    </section>

    <section v-if="auth.isAdmin" class="card">
      <h2>批量导入导出（管理员）</h2>
      <div class="bulk">
        <input
          type="file"
          accept=".csv,text/csv"
          @change="onImportFileChange"
        />
        <button class="btn" :disabled="importing || !importFile" @click="importCsv">
          {{ importing ? "导入中..." : "导入 CSV" }}
        </button>
        <button class="btn secondary" @click="exportCsv">导出 CSV</button>
      </div>
      <p class="sub">
        CSV Header：originalUrl, code(可选), expiresAt(可选，推荐 ISO-8601 Instant，如 2026-03-16T12:34:56Z；兼容旧 LocalDateTime，将按 UTC 解析),
        note(可选), tags(可选, 逗号分隔)
      </p>
    </section>

    <section class="card">
      <div class="cardHead">
        <h2>短链列表</h2>
        <div class="list-actions">
          <button
            class="btn secondary"
            :disabled="loading || !showArchived"
            @click="setArchived(false)"
          >
            活动
          </button>
          <button
            class="btn secondary"
            :disabled="loading || showArchived"
            @click="setArchived(true)"
          >
            归档
          </button>
          <input v-model="keyword" class="keyword" placeholder="搜索短码/URL/备注" />
          <button class="btn secondary" :disabled="loading" @click="load">
            {{ loading ? "刷新中..." : "搜索/刷新" }}
          </button>
        </div>
      </div>
      <p v-if="loading" class="sub">加载中...</p>
      <table class="table" v-else>
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
          <template v-for="it in items" :key="it.id">
            <tr>
              <td class="mono">{{ it.code }}</td>
              <td class="mono">
                <a :href="it.shortUrl" target="_blank" rel="noreferrer">{{ it.shortUrl }}</a>
              </td>
              <td class="mono">{{ it.originalUrl }}</td>
              <td>
                <div v-if="it.note" class="sub">{{ it.note }}</div>
                <div v-if="(it.tags || []).length > 0" class="tags">
                  <span v-for="t in it.tags" :key="t" class="tag">{{ t }}</span>
                </div>
              </td>
              <td class="mono">{{ formatInstantLocal(it.expiresAt) }}</td>
              <td class="mono">{{ policySummary(it) }}</td>
              <td>
                <span :class="it.archivedAt ? 'muted' : it.enabled ? 'ok' : 'bad'">
                  {{ statusLabel(it) }}
                </span>
                <div v-if="it.archivedAt" class="sub">archivedAt: {{ formatInstantLocal(it.archivedAt) }}</div>
              </td>
              <td class="actions-col">
                <button class="btn small secondary" :disabled="!!it.archivedAt" @click="startEdit(it)">
                  编辑
                </button>
                <button class="btn small" :disabled="!!it.archivedAt" @click="toggleEnabled(it)">
                  {{ it.enabled ? "禁用" : "启用" }}
                </button>
                <button
                  v-if="auth.isAdmin && !it.archivedAt"
                  class="btn small secondary"
                  @click="archiveLink(it)"
                >
                  归档
                </button>
                <button
                  v-if="auth.isAdmin && it.archivedAt"
                  class="btn small secondary"
                  @click="restoreLink(it)"
                >
                  恢复
                </button>
                <button
                  v-if="auth.isAdmin && it.archivedAt"
                  class="btn small danger"
                  @click="deleteLink(it)"
                >
                  删除
                </button>
              </td>
            </tr>
            <tr v-if="editingId === it.id">
              <td colspan="8">
                <div class="edit-card">
                  <div class="edit-grid">
                    <label class="field span2">
                      <span class="label">原始链接</span>
                      <input v-model="editOriginalUrl" placeholder="https://example.com" />
                    </label>
                    <label class="field span2">
                      <span class="label">备注</span>
                      <input v-model="editNote" placeholder="备注（可选）" />
                    </label>
                    <label class="field">
                      <span class="label">有效期（expiresAt）</span>
                      <input v-model="editExpiresAt" type="datetime-local" />
                      <span class="hint">留空表示清空有效期</span>
                    </label>
                    <label class="field checkbox">
                      <input type="checkbox" v-model="editEnabled" />
                      <span class="label">启用</span>
                    </label>
                    <label class="field span2">
                      <span class="label">标签（tags）</span>
                      <input v-model="editTags" placeholder="例如：活动A,推广" />
                      <span class="hint">逗号或换行分隔，最多 20 个；保存会覆盖当前标签</span>
                    </label>
                    <label class="field">
                      <span class="label">跳转状态码</span>
                      <select v-model="editRedirectStatusCode">
                        <option value="">继承全局</option>
                        <option value="301">301</option>
                        <option value="302">302</option>
                      </select>
                    </label>
                    <label class="field checkbox">
                      <input type="checkbox" v-model="editPreviewEnabled" />
                      <span class="label">启用预览页</span>
                    </label>
                    <label class="field">
                      <span class="label">Query 透传模式</span>
                      <select v-model="editQueryForwardMode">
                        <option value="">继承全局</option>
                        <option value="OFF">OFF</option>
                        <option value="ALLOWLIST">ALLOWLIST</option>
                        <option value="ALL">ALL</option>
                      </select>
                    </label>
                    <label class="field span2">
                      <span class="label">Query Allowlist</span>
                      <textarea v-model="editQueryForwardAllowlist" rows="2" placeholder="utm_*" />
                      <span class="hint">逗号或换行分隔，支持 utm_* 前缀通配</span>
                    </label>
                    <label class="field span2">
                      <span class="label">不可用落地页（禁用/过期）</span>
                      <input v-model="editUnavailableLandingUrl" placeholder="https://example.com/unavailable" />
                      <span class="hint">清空会回退全局/内置 410；保存时空字符串视为清空</span>
                    </label>
                  </div>
                  <div class="edit-actions">
                    <button class="btn small" @click="saveEdit">保存</button>
                    <button class="btn small secondary" @click="cancelEdit">取消</button>
                  </div>
                </div>
              </td>
            </tr>
          </template>
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
.hint {
  font-size: 12px;
  color: #666;
}
.bulk {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
}
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
.edit-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
</style>
