import {
  API_ENDPOINTS,
  ensureApiSuccess,
  requireApiData,
  withQuery,
} from "./apiContract";
import { apiFetch } from "./http";
import type {
  ApiKeyDto,
  CreateApiKeyRequest,
  CreateApiKeyResponse,
} from "./types";
import { arrayOf, isApiKeyDto, isCreateApiKeyResponse } from "./runtimeContracts";

export async function listApiKeys(applicationId?: number): Promise<ApiKeyDto[]> {
  const response = await apiFetch<ApiKeyDto[]>(
    withQuery(API_ENDPOINTS.apiKeys.collection, { applicationId }),
    {},
    arrayOf(isApiKeyDto),
  );
  return ensureApiSuccess(response, "加载 API Key 失败") ?? [];
}

export async function createApiKey(
  request: CreateApiKeyRequest,
): Promise<CreateApiKeyResponse> {
  const response = await apiFetch<CreateApiKeyResponse>(
    API_ENDPOINTS.apiKeys.collection,
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isCreateApiKeyResponse,
  );
  return requireApiData(response, "创建 API Key 失败");
}

export async function disableApiKey(id: number): Promise<ApiKeyDto> {
  const response = await apiFetch<ApiKeyDto>(
    API_ENDPOINTS.apiKeys.disable(id),
    { method: "PUT" },
    isApiKeyDto,
  );
  return requireApiData(response, "禁用 API Key 失败");
}

export async function enableApiKey(id: number): Promise<ApiKeyDto> {
  const response = await apiFetch<ApiKeyDto>(
    API_ENDPOINTS.apiKeys.enable(id),
    { method: "PUT" },
    isApiKeyDto,
  );
  return requireApiData(response, "启用 API Key 失败");
}

export async function rotateApiKey(id: number): Promise<CreateApiKeyResponse> {
  const response = await apiFetch<CreateApiKeyResponse>(
    API_ENDPOINTS.apiKeys.rotate(id),
    {
      method: "POST",
    },
    isCreateApiKeyResponse,
  );
  return requireApiData(response, "轮换 API Key 失败");
}
