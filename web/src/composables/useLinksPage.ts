import { onMounted, reactive, ref } from "vue";
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

  function setArchived(value: boolean) {
    filters.showArchived = value;
    void load();
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
    importCsv: importExport.importCsv,
    importFileName: importExport.importFileName,
    importing,
    items,
    load,
    policySummary,
    saveEdit: mutations.saveEdit,
    setArchived,
    setImportFile: importExport.setImportFile,
    startEdit: mutations.startEdit,
    statusLabel,
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
