import { apiFetch } from "./http";
import type { ApiResponse, CreateDomainRequest, DomainDto } from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listDomains(): Promise<DomainDto[]> {
  const response = await apiFetch<DomainDto[]>("/api/v1/domains");
  return ensureApiSuccess(response, "加载域名失败") ?? [];
}

export async function listDomainsForApplication(applicationId: number): Promise<DomainDto[]> {
  const response = await apiFetch<DomainDto[]>(`/api/v1/applications/${applicationId}/domains`);
  return ensureApiSuccess(response, "加载应用域名失败") ?? [];
}

export async function createTenantSharedDomain(
  request: CreateDomainRequest,
): Promise<DomainDto> {
  const response = await apiFetch<DomainDto>("/api/v1/domains/tenant-shared", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "创建共享域名失败");
  if (!data) {
    throw new Error("创建共享域名失败");
  }
  return data;
}

export async function createApplicationDomain(
  applicationId: number,
  request: CreateDomainRequest,
): Promise<DomainDto> {
  const response = await apiFetch<DomainDto>(`/api/v1/applications/${applicationId}/domains`, {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "创建专属域名失败");
  if (!data) {
    throw new Error("创建专属域名失败");
  }
  return data;
}

export async function authorizeDomain(
  applicationId: number,
  domainId: number,
): Promise<void> {
  const response = await apiFetch<void>(
    `/api/v1/applications/${applicationId}/domain-authorizations/${domainId}`,
    {
      method: "POST",
    },
  );
  ensureApiSuccess(response, "域名授权失败");
}
