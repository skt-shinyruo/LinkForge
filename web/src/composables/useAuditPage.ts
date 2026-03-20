import { getCurrentInstance, onMounted, ref } from "vue";
import { listAuditLogs } from "../services/audit";
import type { AuditLogDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useAuditPage() {
  const logs = ref<AuditLogDto[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      logs.value = await listAuditLogs();
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载审计日志失败");
    } finally {
      loading.value = false;
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    error,
    load,
    loading,
    logs,
  };
}
