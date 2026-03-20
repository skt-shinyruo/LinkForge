import { apiFetch } from "./http";
import type { ApiResponse, AuditLogDto } from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listAuditLogs(): Promise<AuditLogDto[]> {
  const response = await apiFetch<AuditLogDto[]>("/api/v1/audit-logs");
  return ensureApiSuccess(response, "加载审计日志失败") ?? [];
}
