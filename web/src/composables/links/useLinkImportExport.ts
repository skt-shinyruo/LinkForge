import { computed, type Ref } from "vue";
import { exportLinksCsv, importLinksCsv } from "../../services/links";
import type { LinkExportQuery, LinkImportQuery } from "../../services/types";

export function useLinkImportExport(args: {
  importFile: Ref<File | null>;
  importing: Ref<boolean>;
  setError: (message: string | null) => void;
  getErrorMessage: (error: unknown, fallbackMessage: string) => string;
  getImportQuery?: () => LinkImportQuery;
  getExportQuery: () => LinkExportQuery;
  reload: () => Promise<void>;
}) {
  const { importFile, importing, setError, getErrorMessage, getImportQuery, getExportQuery, reload } = args;

  const importFileName = computed(() => importFile.value?.name ?? "");

  function setImportFile(file: File | null) {
    importFile.value = file;
  }

  async function importCsv() {
    if (!importFile.value) {
      return;
    }

    importing.value = true;
    setError(null);

    try {
      await importLinksCsv(importFile.value, getImportQuery?.() ?? {});
      importFile.value = null;
      await reload();
    } catch (caught) {
      setError(getErrorMessage(caught, "导入失败"));
    } finally {
      importing.value = false;
    }
  }

  async function exportCsv() {
    setError(null);

    try {
      const blob = await exportLinksCsv({ page: 0, size: 1000, ...getExportQuery() });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "links.csv";
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setError(getErrorMessage(caught, "导出失败"));
    }
  }

  return {
    exportCsv,
    importCsv,
    importFileName,
    setImportFile,
  };
}
