import { API_ENDPOINTS, ensureApiSuccess, requireApiData } from "./apiContract";
import { apiFetch } from "./http";
import type {
  ApprovalRequestDto,
  ApproveRequest,
} from "./types";
import { arrayOf, isApprovalRequestDto } from "./runtimeContracts";

export async function listApprovals(): Promise<ApprovalRequestDto[]> {
  const response = await apiFetch<ApprovalRequestDto[]>(
    API_ENDPOINTS.approvals.collection,
    {},
    arrayOf(isApprovalRequestDto),
  );
  return ensureApiSuccess(response, "加载审批失败") ?? [];
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
