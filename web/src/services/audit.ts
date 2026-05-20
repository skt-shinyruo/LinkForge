import { API_ENDPOINTS, ensureApiSuccess } from "./apiContract";
import { apiFetch } from "./http";
import type { AuditLogDto } from "./types";

export async function listAuditLogs(): Promise<AuditLogDto[]> {
  const response = await apiFetch<AuditLogDto[]>(API_ENDPOINTS.auditLogs.collection);
  return ensureApiSuccess(response, "加载审计日志失败") ?? [];
}
