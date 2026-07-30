import { computed, ref, type Ref } from "vue";
import { exportLinksCsv, importLinksCsv } from "../../services/links";
import type { LinkExportQuery, LinkImportQuery, LinkImportResult } from "../../services/types";

/**
 * 编排 CSV 导入导出。
 *
 * 导入结果逐行展示并在完成后刷新列表；应用级 scope 由调用方 query 显式提供。导出固定请求最多 1000 条，
 * Blob URL 仅在触发浏览器下载期间存活，不把认证信息放入下载 URL。
 */
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

  const importResult = ref<LinkImportResult | null>(null);
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
    importResult.value = null;

    try {
      importResult.value = await importLinksCsv(importFile.value, getImportQuery?.() ?? {});
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
    importResult,
    setImportFile,
  };
}
