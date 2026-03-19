import { getCurrentInstance, onMounted, reactive, ref } from "vue";
import { listLinks } from "../services/links";
import type { LinkDto } from "../services/types";
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
  const editingId = ref<number | null>(null);
  const importFile = ref<File | null>(null);
  const page = ref(0);
  const size = ref(DEFAULT_PAGE_SIZE);
  const total = ref(0);

  const filters = reactive<LinkListFilters>({
    showArchived: false,
    keyword: "",
  });

  const createForm = reactive<LinkCreateFormState>(createEmptyCreateForm());
  const editForm = reactive<LinkEditFormState>(createEmptyEditForm());

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

  const mutations = useLinkMutations({
    createForm,
    editForm,
    editingId,
    creating,
    filters,
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
    reload: load,
  });

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    createForm,
    creating,
    editForm,
    editingId,
    error,
    filters,
    formatInstantLocal,
    importCsv: importExport.importCsv,
    importFileName: importExport.importFileName,
    importing,
    items,
    load,
    nextPage,
    page,
    policySummary,
    previousPage,
    saveEdit: mutations.saveEdit,
    setArchived,
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
