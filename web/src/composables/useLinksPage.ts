import { computed, getCurrentInstance, onMounted, reactive, ref } from "vue";
import { listApplications } from "../services/applications";
import { listDomainsForApplication } from "../services/domains";
import { listLinks } from "../services/links";
import type { ApplicationDto, DomainDto, LinkDto } from "../services/types";
import { useAuthStore } from "../stores/auth";
import {
  createEmptyCreateForm,
  createEmptyEditForm,
  formatInstantLocal,
  policySummary,
  statusLabel,
  type LinkCreateFormState,
  type LinkEditFormState,
} from "./links/linkFormCodec";
import { useLinkImportExport } from "./links/useLinkImportExport";
import { useLinkMutations } from "./links/useLinkMutations";

export type { LinkCreateFormState, LinkEditFormState } from "./links/linkFormCodec";

export type LinkListFilters = {
  showArchived: boolean;
  keyword: string;
};

const DEFAULT_PAGE_SIZE = 50;

function getErrorMessage(error: unknown, fallbackMessage: string): string {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useLinksPage() {
  const loading = ref(false);
  const creating = ref(false);
  const importing = ref(false);
  const error = ref<string | null>(null);
  const items = ref<LinkDto[]>([]);
  const applications = ref<ApplicationDto[]>([]);
  const availableDomains = ref<DomainDto[]>([]);
  const editingId = ref<number | null>(null);
  const importFile = ref<File | null>(null);
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

  const auth = useAuthStore();
  const isAdmin = computed(() => auth.isTenantAdmin);

  function resetCreateForm() {
    Object.assign(createForm, createEmptyCreateForm());
  }

  function resetEditForm() {
    Object.assign(editForm, createEmptyEditForm());
  }

  async function load(targetPage = page.value) {
    loading.value = true;
    error.value = null;

    try {
      const response = await listLinks({
        page: targetPage,
        size: size.value,
        applicationId: selectedApplicationId.value ?? undefined,
        archived: filters.showArchived,
        keyword: filters.keyword.trim() || undefined,
      });
      items.value = response.items;
      total.value = response.total;
      page.value = response.page;
      size.value = response.size;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载失败");
    } finally {
      loading.value = false;
    }
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
      availableDomains.value = [];
      selectedDomainId.value = null;
      return;
    }
    const nextDomains = await listDomainsForApplication(applicationId);
    availableDomains.value = nextDomains;
    if (nextDomains.length === 0) {
      selectedDomainId.value = null;
      return;
    }
    if (!nextDomains.some((domain) => domain.id === selectedDomainId.value)) {
      selectedDomainId.value = nextDomains[0]!.id;
    }
  }

  const mutations = useLinkMutations({
    createForm,
    editForm,
    editingId,
    creating,
    filters,
    selectedApplicationId,
    selectedDomainId,
    setError: (message) => {
      error.value = message;
    },
    getErrorMessage,
    load,
    resetCreateForm,
    resetEditForm,
  });

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

  const importExport = useLinkImportExport({
    importFile,
    importing,
    setError: (message) => {
      error.value = message;
    },
    getErrorMessage,
    getImportQuery: () => {
      if (selectedApplicationId.value != null && selectedDomainId.value == null) {
        throw new Error("请选择应用域名");
      }
      return {
        applicationId: selectedApplicationId.value ?? undefined,
        domainId: selectedDomainId.value ?? undefined,
      };
    },
    getExportQuery: () => ({
      applicationId: selectedApplicationId.value ?? undefined,
      archived: filters.showArchived,
      keyword: filters.keyword.trim() || undefined,
    }),
    reload: load,
  });

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

  if (getCurrentInstance()) {
    onMounted(() => {
      void (async () => {
        if (isAdmin.value) {
          await loadAdminOptions();
        }
        await load();
      })();
    });
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
    formatInstantLocal,
    importCsv: importExport.importCsv,
    importFileName: importExport.importFileName,
    importResult: importExport.importResult,
    importing,
    items,
    load,
    nextPage,
    page,
    policySummary,
    previousPage,
    saveEdit: mutations.saveEdit,
    selectedApplicationId,
    selectedDomainId,
    setArchived,
    setSelectedApplicationId,
    setSelectedDomainId,
    setKeyword,
    setImportFile: importExport.setImportFile,
    size,
    startEdit: mutations.startEdit,
    statusLabel,
    total,
    toggleEnabled: mutations.toggleEnabled,
    archiveLink: mutations.archiveLink,
    cancelEdit: mutations.cancelEdit,
    createLink: mutations.createLink,
    deleteLink: mutations.deleteLink,
    exportCsv: importExport.exportCsv,
    restoreLink: mutations.restoreLink,
    loading,
  };
}
