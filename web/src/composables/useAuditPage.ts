import { ref } from "vue";
import { listAuditLogs } from "../services/audit";
import type { AuditLogDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useAuditPage() {
  const logs = ref<AuditLogDto[]>([]);
  const loading = ref(false);
  const hasMore = ref(false);
  const nextCursor = ref<string | null>(null);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const page = await listAuditLogs();
      logs.value = page.items;
      hasMore.value = page.hasMore;
      nextCursor.value = page.nextCursor;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载审计日志失败");
    } finally {
      loading.value = false;
    }
  }

  async function loadMore() {
    if (loading.value || !hasMore.value || !nextCursor.value) {
      return;
    }
    loading.value = true;
    error.value = null;
    try {
      const page = await listAuditLogs({ cursor: nextCursor.value });
      logs.value = [...logs.value, ...page.items];
      hasMore.value = page.hasMore;
      nextCursor.value = page.nextCursor;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载审计日志失败");
    } finally {
      loading.value = false;
    }
  }

  return {
    error,
    hasMore,
    load,
    loadMore,
    loading,
    logs,
  };
}
