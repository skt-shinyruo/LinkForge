import { apiFetch } from "./http";
import type {
  ApiResponse,
  ApiKeyDto,
  CreateApiKeyRequest,
  CreateApiKeyResponse,
} from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listApiKeys(applicationId?: number): Promise<ApiKeyDto[]> {
  const params = new URLSearchParams();
  if (applicationId !== undefined) {
    params.set("applicationId", String(applicationId));
  }
  const query = params.toString();
  const response = await apiFetch<ApiKeyDto[]>(
    `/api/v1/api-keys${query ? `?${query}` : ""}`,
  );
  return ensureApiSuccess(response, "加载 API Key 失败") ?? [];
}

export async function createApiKey(
  request: CreateApiKeyRequest,
): Promise<CreateApiKeyResponse> {
  const response = await apiFetch<CreateApiKeyResponse>("/api/v1/api-keys", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "创建 API Key 失败");
  if (!data) {
    throw new Error("创建 API Key 失败");
  }
  return data;
}

export async function disableApiKey(id: number): Promise<ApiKeyDto> {
  const response = await apiFetch<ApiKeyDto>(`/api/v1/api-keys/${id}/disable`, {
    method: "PUT",
  });
  const data = ensureApiSuccess(response, "禁用 API Key 失败");
  if (!data) {
    throw new Error("禁用 API Key 失败");
  }
  return data;
}

export async function enableApiKey(id: number): Promise<ApiKeyDto> {
  const response = await apiFetch<ApiKeyDto>(`/api/v1/api-keys/${id}/enable`, {
    method: "PUT",
  });
  const data = ensureApiSuccess(response, "启用 API Key 失败");
  if (!data) {
    throw new Error("启用 API Key 失败");
  }
  return data;
}

export async function rotateApiKey(id: number): Promise<CreateApiKeyResponse> {
  const response = await apiFetch<CreateApiKeyResponse>(`/api/v1/api-keys/${id}/rotate`, {
    method: "POST",
  });
  const data = ensureApiSuccess(response, "轮换 API Key 失败");
  if (!data) {
    throw new Error("轮换 API Key 失败");
  }
  return data;
}
