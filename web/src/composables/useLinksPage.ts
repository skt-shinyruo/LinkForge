import { computed, onMounted, reactive, ref } from "vue";
import {
  archiveLink as archiveLinkRequest,
  createLink as createLinkRequest,
  deleteLink as deleteLinkRequest,
  exportLinksCsv,
  importLinksCsv,
  listLinks,
  restoreLink as restoreLinkRequest,
  updateLink,
} from "../services/links";
import type { LinkDto, QueryForwardMode, UpdateLinkRequest } from "../services/types";

type RedirectStatusFieldValue = "" | "301" | "302";

type QueryForwardModeFieldValue = "" | QueryForwardMode;

export type LinkListFilters = {
  showArchived: boolean;
  keyword: string;
};

export type LinkCreateFormState = {
  originalUrl: string;
  note: string;
  customCode: string;
  expiresAt: string;
  tags: string;
  enabled: boolean;
  redirectStatusCode: RedirectStatusFieldValue;
  previewEnabled: boolean;
  unavailableLandingUrl: string;
  queryForwardMode: QueryForwardModeFieldValue;
  queryForwardAllowlist: string;
};

export type LinkEditFormState = {
  originalUrl: string;
  note: string;
  expiresAt: string;
  tags: string;
  enabled: boolean;
  redirectStatusCode: RedirectStatusFieldValue;
  previewEnabled: boolean;
  unavailableLandingUrl: string;
  queryForwardMode: QueryForwardModeFieldValue;
  queryForwardAllowlist: string;
};

function createEmptyCreateForm(): LinkCreateFormState {
  return {
    originalUrl: "",
    note: "",
    customCode: "",
    expiresAt: "",
    tags: "",
    enabled: true,
    redirectStatusCode: "",
    previewEnabled: false,
    unavailableLandingUrl: "",
    queryForwardMode: "",
    queryForwardAllowlist: "",
  };
}

function createEmptyEditForm(): LinkEditFormState {
  return {
    originalUrl: "",
    note: "",
    expiresAt: "",
    tags: "",
    enabled: true,
    redirectStatusCode: "",
    previewEnabled: false,
    unavailableLandingUrl: "",
    queryForwardMode: "",
    queryForwardAllowlist: "",
  };
}

function getErrorMessage(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage;
}

function parseAllowlist(raw: string): string[] | null {
  const value = raw.trim();
  if (!value) {
    return null;
  }

  const parts = value
    .split(/[\n,]+/g)
    .map((item) => item.trim())
    .filter(Boolean);

  return Array.from(new Set(parts));
}

function parseTags(raw: string): string[] {
  const value = raw.trim();
  if (!value) {
    return [];
  }

  const parts = value
    .split(/[\n,]+/g)
    .map((item) => item.trim())
    .filter(Boolean);

  return Array.from(new Set(parts)).slice(0, 20);
}

function dateTimeLocalToInstantString(raw: string): string | undefined {
  const value = raw.trim();
  if (!value) {
    return undefined;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

function instantStringToDateTimeLocalInput(value?: string | null): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

function formatInstantLocal(value?: string | null): string {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

function policySummary(link: LinkDto): string {
  const redirectStatusCode =
    link.redirectStatusCode === 301 || link.redirectStatusCode === 302
      ? String(link.redirectStatusCode)
      : "默认";
  const preview = link.previewEnabled ? "Preview" : "";
  const queryForwardMode = link.queryForwardMode || "默认";
  return [redirectStatusCode, queryForwardMode, preview].filter(Boolean).join(" / ");
}

function statusLabel(link: LinkDto): string {
  if (link.archivedAt) {
    return "已归档";
  }
  return link.enabled ? "启用" : "禁用";
}

export function useLinksPage() {
  const loading = ref(false);
  const creating = ref(false);
  const importing = ref(false);
  const error = ref<string | null>(null);
  const items = ref<LinkDto[]>([]);
  const editingId = ref<number | null>(null);
  const importFile = ref<File | null>(null);

  const filters = reactive<LinkListFilters>({
    showArchived: false,
    keyword: "",
  });

  const createForm = reactive<LinkCreateFormState>(createEmptyCreateForm());
  const editForm = reactive<LinkEditFormState>(createEmptyEditForm());

  const importFileName = computed(() => importFile.value?.name ?? "");

  function resetCreateForm() {
    Object.assign(createForm, createEmptyCreateForm());
  }

  function resetEditForm() {
    Object.assign(editForm, createEmptyEditForm());
  }

  async function load() {
    loading.value = true;
    error.value = null;

    try {
      const response = await listLinks({
        page: 0,
        size: 50,
        archived: filters.showArchived,
        keyword: filters.keyword.trim() || undefined,
      });
      items.value = response.items;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载失败");
    } finally {
      loading.value = false;
    }
  }

  async function createLink() {
    creating.value = true;
    error.value = null;

    try {
      const allowlist = parseAllowlist(createForm.queryForwardAllowlist);
      const tags = parseTags(createForm.tags);
      const expiresAt = dateTimeLocalToInstantString(createForm.expiresAt);
      const redirectStatusCode =
        createForm.redirectStatusCode === "301"
          ? 301
          : createForm.redirectStatusCode === "302"
            ? 302
            : undefined;

      await createLinkRequest({
        originalUrl: createForm.originalUrl,
        note: createForm.note || undefined,
        enabled: createForm.enabled,
        customCode: createForm.customCode.trim() || undefined,
        expiresAt,
        tags: tags.length > 0 ? tags : undefined,
        redirectStatusCode,
        previewEnabled: createForm.previewEnabled,
        unavailableLandingUrl: createForm.unavailableLandingUrl.trim() || undefined,
        queryForwardMode: createForm.queryForwardMode || undefined,
        queryForwardAllowlist: allowlist && allowlist.length > 0 ? allowlist : undefined,
      });

      filters.showArchived = false;
      resetCreateForm();
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "创建失败");
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

      await updateLink(link.id, { enabled: !link.enabled });
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "更新失败");
    }
  }

  function startEdit(link: LinkDto) {
    if (link.archivedAt) {
      error.value = "短链已归档，请先恢复后再编辑";
      return;
    }

    editingId.value = link.id;
    editForm.originalUrl = link.originalUrl || "";
    editForm.note = link.note || "";
    editForm.expiresAt = instantStringToDateTimeLocalInput(link.expiresAt);
    editForm.tags = (link.tags || []).join(",");
    editForm.enabled = !!link.enabled;
    editForm.redirectStatusCode = link.redirectStatusCode === 301
      ? "301"
      : link.redirectStatusCode === 302
        ? "302"
        : "";
    editForm.previewEnabled = !!link.previewEnabled;
    editForm.unavailableLandingUrl = link.unavailableLandingUrl || "";
    editForm.queryForwardMode = link.queryForwardMode || "";
    editForm.queryForwardAllowlist = (link.queryForwardAllowlist || []).join(",");
  }

  function cancelEdit() {
    editingId.value = null;
    resetEditForm();
  }

  async function saveEdit() {
    if (!editingId.value) {
      return;
    }

    error.value = null;

    try {
      const originalUrl = editForm.originalUrl.trim();
      if (!originalUrl) {
        throw new Error("原始链接不能为空");
      }

      const allowlist = parseAllowlist(editForm.queryForwardAllowlist) || [];
      const expiresAt = dateTimeLocalToInstantString(editForm.expiresAt);
      const tags = parseTags(editForm.tags);
      const payload: UpdateLinkRequest = {
        originalUrl,
        note: editForm.note,
        enabled: editForm.enabled,
        previewEnabled: editForm.previewEnabled,
        unavailableLandingUrl: editForm.unavailableLandingUrl,
        queryForwardAllowlist: allowlist,
        tags,
      };

      if (expiresAt) {
        payload.expiresAt = expiresAt;
      } else {
        payload.clearExpiresAt = true;
      }

      if (editForm.redirectStatusCode) {
        payload.redirectStatusCode = editForm.redirectStatusCode === "301" ? 301 : 302;
      } else {
        payload.clearRedirectStatusCode = true;
      }

      if (editForm.queryForwardMode) {
        payload.queryForwardMode = editForm.queryForwardMode;
      } else {
        payload.clearQueryForwardMode = true;
      }

      await updateLink(editingId.value, payload);
      cancelEdit();
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "更新失败");
    }
  }

  async function archiveLink(link: LinkDto) {
    error.value = null;

    try {
      await archiveLinkRequest(link.id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "归档失败");
    }
  }

  async function restoreLink(link: LinkDto) {
    error.value = null;

    try {
      await restoreLinkRequest(link.id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "恢复失败");
    }
  }

  async function deleteLink(link: LinkDto) {
    error.value = null;

    try {
      if (!link.archivedAt) {
        throw new Error("删除前请先归档");
      }

      const confirmed = window.confirm(`确认删除短链 ${link.code}？该操作不可恢复。`);
      if (!confirmed) {
        return;
      }

      await deleteLinkRequest(link.id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "删除失败");
    }
  }

  function setArchived(value: boolean) {
    filters.showArchived = value;
    void load();
  }

  function setImportFile(file: File | null) {
    importFile.value = file;
  }

  async function importCsv() {
    if (!importFile.value) {
      return;
    }

    importing.value = true;
    error.value = null;

    try {
      await importLinksCsv(importFile.value);
      importFile.value = null;
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "导入失败");
    } finally {
      importing.value = false;
    }
  }

  async function exportCsv() {
    error.value = null;

    try {
      const blob = await exportLinksCsv({ page: 0, size: 1000 });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "links.csv";
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      error.value = getErrorMessage(caught, "导出失败");
    }
  }

  onMounted(() => {
    void load();
  });

  return {
    createForm,
    creating,
    editForm,
    editingId,
    error,
    filters,
    formatInstantLocal,
    importCsv,
    importFileName,
    importing,
    items,
    load,
    policySummary,
    saveEdit,
    setArchived,
    setImportFile,
    startEdit,
    statusLabel,
    toggleEnabled,
    archiveLink,
    cancelEdit,
    createLink,
    deleteLink,
    exportCsv,
    restoreLink,
    loading,
  };
}
