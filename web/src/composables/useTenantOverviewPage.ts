import { computed, ref } from "vue";
import { listApplications } from "../services/applications";
import { listApprovals } from "../services/approvals";
import { listAuditLogs } from "../services/audit";
import { listDomains } from "../services/domains";
import type { ApplicationDto, ApprovalRequestDto, AuditLogDto, DomainDto } from "../services/types";

function getErrorMessage(error: unknown, fallbackMessage: string) {
  return error instanceof Error ? error.message : fallbackMessage;
}

export function useTenantOverviewPage() {
  const applications = ref<ApplicationDto[]>([]);
  const domains = ref<DomainDto[]>([]);
  const approvals = ref<ApprovalRequestDto[]>([]);
  const auditLogs = ref<AuditLogDto[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const pendingApprovalCount = computed(
    () => approvals.value.filter((approval) => approval.status === "PENDING_APPROVAL").length,
  );

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const [nextApplications, nextDomains, nextApprovals, nextAuditLogs] = await Promise.all([
        listApplications(),
        listDomains(),
        listApprovals(),
        listAuditLogs(),
      ]);
      applications.value = nextApplications;
      domains.value = nextDomains;
      approvals.value = nextApprovals.items;
      auditLogs.value = nextAuditLogs.items;
    } catch (caught) {
      error.value = getErrorMessage(caught, "加载概览失败");
    } finally {
      loading.value = false;
    }
  }

  return {
    applications,
    approvals,
    auditLogs,
    domains,
    error,
    load,
    loading,
    pendingApprovalCount,
  };
}
