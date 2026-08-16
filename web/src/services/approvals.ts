import { API_ENDPOINTS, ensureApiSuccess, requireApiData, withQuery } from "./apiContract";
import { apiFetch } from "./http";
import { readCursorPageHeaders } from "./cursorPagination";
import type {
  ApprovalRequestDto,
  ApprovalListQuery,
  ApproveRequest,
  CursorPageResponse,
} from "./types";
import { arrayOf, isApprovalRequestDto } from "./runtimeContracts";

export async function listApprovals(query: ApprovalListQuery = {}): Promise<CursorPageResponse<ApprovalRequestDto>> {
  let pageHeaders = { hasMore: false, nextCursor: null as string | null };
  const response = await apiFetch<ApprovalRequestDto[]>(
    withQuery(API_ENDPOINTS.approvals.collection, query),
    {},
    arrayOf(isApprovalRequestDto),
    (rawResponse) => {
      pageHeaders = readCursorPageHeaders(rawResponse);
    },
  );
  return {
    items: ensureApiSuccess(response, "加载审批失败") ?? [],
    ...pageHeaders,
  };
}

export async function approveRequest(
  requestId: number,
  request: ApproveRequest,
): Promise<ApprovalRequestDto> {
  const response = await apiFetch<ApprovalRequestDto>(
    API_ENDPOINTS.approvals.approve(requestId),
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isApprovalRequestDto,
  );
  return requireApiData(response, "审批失败");
}
