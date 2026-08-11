import { API_ENDPOINTS, ensureApiSuccess, requireApiData } from "./apiContract";
import { apiFetch } from "./http";
import type { CreateDomainRequest, DomainDto } from "./types";
import { arrayOf, isDomainDto } from "./runtimeContracts";

export async function listDomains(): Promise<DomainDto[]> {
  const response = await apiFetch<DomainDto[]>(
    API_ENDPOINTS.domains.collection,
    {},
    arrayOf(isDomainDto),
  );
  return ensureApiSuccess(response, "加载域名失败") ?? [];
}

export async function listDomainsForApplication(
  applicationId: number,
  options: Pick<RequestInit, "signal"> = {},
): Promise<DomainDto[]> {
  const response = await apiFetch<DomainDto[]>(
    API_ENDPOINTS.applications.domains(applicationId),
    options,
    arrayOf(isDomainDto),
  );
  return ensureApiSuccess(response, "加载应用域名失败") ?? [];
}

export async function createTenantSharedDomain(
  request: CreateDomainRequest,
): Promise<DomainDto> {
  const response = await apiFetch<DomainDto>(
    API_ENDPOINTS.domains.tenantShared,
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isDomainDto,
  );
  return requireApiData(response, "创建共享域名失败");
}

export async function createApplicationDomain(
  applicationId: number,
  request: CreateDomainRequest,
): Promise<DomainDto> {
  const response = await apiFetch<DomainDto>(
    API_ENDPOINTS.applications.domains(applicationId),
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isDomainDto,
  );
  return requireApiData(response, "创建专属域名失败");
}

export async function authorizeDomain(
  applicationId: number,
  domainId: number,
): Promise<void> {
  const response = await apiFetch<void>(
    API_ENDPOINTS.applications.domainAuthorization(applicationId, domainId),
    {
      method: "POST",
    },
  );
  ensureApiSuccess(response, "域名授权失败");
}
