import { API_ENDPOINTS, ensureApiSuccess, withQuery } from "./apiContract";
import { apiFetch } from "./http";
import { readCursorPageHeaders } from "./cursorPagination";
import type { AuditLogDto, AuditLogListQuery, CursorPageResponse } from "./types";
import { arrayOf, isAuditLogDto } from "./runtimeContracts";

export async function listAuditLogs(query: AuditLogListQuery = {}): Promise<CursorPageResponse<AuditLogDto>> {
  let pageHeaders = { hasMore: false, nextCursor: null as string | null };
  const response = await apiFetch<AuditLogDto[]>(
    withQuery(API_ENDPOINTS.auditLogs.collection, query),
    {},
    arrayOf(isAuditLogDto),
    (rawResponse) => {
      pageHeaders = readCursorPageHeaders(rawResponse);
    },
  );
  return {
    items: ensureApiSuccess(response, "加载审计日志失败") ?? [],
    ...pageHeaders,
  };
}
