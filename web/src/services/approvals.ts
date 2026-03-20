import { apiFetch } from "./http";
import type {
  ApiResponse,
  ApprovalRequestDto,
  ApproveRequest,
} from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listApprovals(): Promise<ApprovalRequestDto[]> {
  const response = await apiFetch<ApprovalRequestDto[]>("/api/v1/approvals");
  return ensureApiSuccess(response, "加载审批失败") ?? [];
}

export async function approveRequest(
  requestId: number,
  request: ApproveRequest,
): Promise<ApprovalRequestDto> {
  const response = await apiFetch<ApprovalRequestDto>(`/api/v1/approvals/${requestId}/approve`, {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "审批失败");
  if (!data) {
    throw new Error("审批失败");
  }
  return data;
}
