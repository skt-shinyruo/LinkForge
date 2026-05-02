import { getCurrentInstance, onMounted, reactive, ref } from "vue";
import { approveRequest, listApprovals } from "../services/approvals";
import type { ApprovalRequestDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useApprovalsPage() {
  const approvals = ref<ApprovalRequestDto[]>([]);
  const loading = ref(false);
  const actingId = ref<number | null>(null);
  const error = ref<string | null>(null);
  const decisionReasons = reactive<Record<number, string>>({});

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      approvals.value = await listApprovals();
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载审批失败");
    } finally {
      loading.value = false;
    }
  }

  function setDecisionReason(requestId: number, reason: string) {
    decisionReasons[requestId] = reason;
  }

  async function approve(requestId: number, reason = decisionReasons[requestId] ?? "") {
    actingId.value = requestId;
    error.value = null;
    try {
      await approveRequest(requestId, { reason: reason.trim() || undefined });
      delete decisionReasons[requestId];
      await load();
    } catch (caught) {
      error.value = getErrorMessage(caught, "审批失败");
    } finally {
      actingId.value = null;
    }
  }

  if (getCurrentInstance()) {
    onMounted(() => {
      void load();
    });
  }

  return {
    actingId,
    approvals,
    approve,
    decisionReasons,
    error,
    load,
    loading,
    setDecisionReason,
  };
}
