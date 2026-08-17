import { computed, reactive, ref } from "vue";
import { listApplications } from "../services/applications";
import { listDomainsForApplication } from "../services/domains";
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
import type { ApplicationDto, DomainDto, LinkDto, LinkImportResult } from "../services/types";
import { useAuthStore } from "../stores/auth";
import {
  buildCreatePayload,
  buildEditPayload,
  createEmptyCreateForm,
  createEmptyEditForm,
  fillEditFormFromLink,
  type LinkCreateFormState,
  type LinkEditFormState,
} from "./links/linkFormCodec";
import { useLatestRequest } from "./useLatestRequest";

export type LinkListFilters = {
  showArchived: boolean;
  keyword: string;
};

const DEFAULT_PAGE_SIZE = 50;

function getErrorMessage(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useLinksPage() {
  const creating = ref(false);
  const importing = ref(false);
  const items = ref<LinkDto[]>([]);
  const applications = ref<ApplicationDto[]>([]);
  const availableDomains = ref<DomainDto[]>([]);
  const editingId = ref<number | null>(null);
  const importFile = ref<File | null>(null);
  const importResult = ref<LinkImportResult | null>(null);
  const page = ref(0);
  const size = ref(DEFAULT_PAGE_SIZE);
  const total = ref(0);
  const selectedApplicationId = ref<number | null>(null);
  const selectedDomainId = ref<number | null>(null);

  const filters = reactive<LinkListFilters>({
    showArchived: false,
    keyword: "",
  });

  const createForm = reactive<LinkCreateFormState>(createEmptyCreateForm());
  const editForm = reactive<LinkEditFormState>(createEmptyEditForm());
  const importFileName = computed(() => importFile.value?.name ?? "");

  const auth = useAuthStore();
  const isAdmin = computed(() => auth.isTenantAdmin);
  const latestLoad = useLatestRequest();
  const latestDomainLoad = useLatestRequest();
  const loading = latestLoad.loading;
  const error = latestLoad.error;

  function resetCreateForm() {
    Object.assign(createForm, createEmptyCreateForm());
  }

  function resetEditForm() {
    Object.assign(editForm, createEmptyEditForm());
  }

  async function load(targetPage = page.value) {
    const query = {
      page: targetPage,
      size: size.value,
      applicationId: selectedApplicationId.value ?? undefined,
      archived: filters.showArchived,
      keyword: filters.keyword.trim() || undefined,
    };
    await latestLoad.run(
      (signal) => listLinks(query, { signal }),
      (response) => {
        items.value = response.items;
        total.value = response.total;
        page.value = response.page;
        size.value = response.size;
      },
      "加载失败",
    );
  }

  async function loadAdminOptions() {
    try {
      const nextApplications = await listApplications();
      applications.value = nextApplications;
      if (
        selectedApplicationId.value != null &&
        !nextApplications.some((application) => application.id === selectedApplicationId.value)
      ) {
        selectedApplicationId.value = null;
      }
      if (selectedApplicationId.value != null) {
        await loadDomainsForApplication(selectedApplicationId.value);
      }
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载应用和域名失败");
    }
  }

  async function loadDomainsForApplication(applicationId: number | null) {
    if (!isAdmin.value || applicationId == null) {
      latestDomainLoad.cancel();
      availableDomains.value = [];
      selectedDomainId.value = null;
      return;
    }
    await latestDomainLoad.run(
      (signal) => listDomainsForApplication(applicationId, { signal }),
      (nextDomains) => {
        availableDomains.value = nextDomains;
        if (nextDomains.length === 0) {
          selectedDomainId.value = null;
          return;
        }
        if (!nextDomains.some((domain) => domain.id === selectedDomainId.value)) {
          selectedDomainId.value = nextDomains[0]!.id;
        }
      },
      "加载应用域名失败",
    );
    if (latestDomainLoad.error.value) {
      error.value = latestDomainLoad.error.value;
    }
  }

  async function createLink() {
    creating.value = true;
    error.value = null;
    try {
      const payload = buildCreatePayload(createForm);
      if (selectedApplicationId.value != null) {
        if (selectedDomainId.value == null) {
          throw new Error("请选择应用域名");
        }
        payload.applicationId = selectedApplicationId.value;
        payload.domainId = selectedDomainId.value;
      }
      await createLinkRequest(payload);
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
    fillEditFormFromLink(editForm, link);
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
      if (!editForm.originalUrl.trim()) {
        throw new Error("原始链接不能为空");
      }
      const updated = await updateLink(editingId.value, buildEditPayload(editForm));
      cancelEdit();
      await load();
      if (updated.pendingApproval) {
        const approvalId = updated.approvalRequestId == null ? "" : `（#${updated.approvalRequestId}）`;
        error.value = `目标地址变更已提交审批${approvalId}，审批通过后生效`;
      }
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
      if (!window.confirm(`确认删除短链 ${link.code}？该操作不可恢复。`)) {
        return;
      }
      await deleteLinkRequest(link.id);
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "删除失败");
    }
  }

  function setKeyword(value: string) {
    filters.keyword = value;
    page.value = 0;
  }

  async function setArchived(value: boolean) {
    filters.showArchived = value;
    page.value = 0;
    await load(0);
  }

  async function previousPage() {
    if (loading.value || page.value <= 0) {
      return;
    }
    await load(page.value - 1);
  }

  async function nextPage() {
    if (loading.value || (page.value + 1) * size.value >= total.value) {
      return;
    }
    await load(page.value + 1);
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
    importResult.value = null;
    try {
      if (selectedApplicationId.value != null && selectedDomainId.value == null) {
        throw new Error("请选择应用域名");
      }
      importResult.value = await importLinksCsv(importFile.value, {
        applicationId: selectedApplicationId.value ?? undefined,
        domainId: selectedDomainId.value ?? undefined,
      });
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
      const blob = await exportLinksCsv({
        page: 0,
        size: 1000,
        applicationId: selectedApplicationId.value ?? undefined,
        archived: filters.showArchived,
        keyword: filters.keyword.trim() || undefined,
      });
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

  async function setSelectedApplicationId(value: number | null) {
    selectedApplicationId.value = value;
    try {
      await loadDomainsForApplication(value);
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载域名失败");
    }
    page.value = 0;
    await load(0);
  }

  function setSelectedDomainId(value: number | null) {
    selectedDomainId.value = value;
  }

  async function init() {
    if (isAdmin.value) {
      await loadAdminOptions();
    }
    await load();
  }

  return {
    applications,
    availableDomains,
    createForm,
    creating,
    editForm,
    editingId,
    error,
    filters,
    importCsv,
    importFileName,
    importResult,
    importing,
    init,
    items,
    load,
    nextPage,
    page,
    previousPage,
    saveEdit,
    selectedApplicationId,
    selectedDomainId,
    setArchived,
    setSelectedApplicationId,
    setSelectedDomainId,
    setKeyword,
    setImportFile,
    size,
    startEdit,
    total,
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
