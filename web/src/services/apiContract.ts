import type { ApiResponse } from "./types";

const API_V1 = "/api/v1";

type QueryValue = string | number | boolean | null | undefined;

type QueryStringOptions = {
  skipEmptyString?: boolean;
};

export function ensureApiSuccess<T>(
  response: ApiResponse<T>,
  fallbackMessage: string,
): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export function requireApiData<T>(
  response: ApiResponse<T>,
  fallbackMessage: string,
): T {
  const data = ensureApiSuccess(response, fallbackMessage);
  if (data === undefined) {
    throw new Error(fallbackMessage);
  }
  return data;
}

export function buildQueryString(
  values: Record<string, QueryValue>,
  options: QueryStringOptions = {},
): string {
  const skipEmptyString = options.skipEmptyString ?? true;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined || value === null) {
      continue;
    }
    if (skipEmptyString && value === "") {
      continue;
    }
    params.set(key, String(value));
  }
  return params.toString();
}

export function withQuery(
  path: string,
  values: Record<string, QueryValue>,
  options?: QueryStringOptions,
): string {
  const queryString = buildQueryString(values, options);
  return queryString ? `${path}?${queryString}` : path;
}

export async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    return {} as ApiResponse<T>;
  }
  return JSON.parse(text) as ApiResponse<T>;
}

function applicationPath(applicationId: number): string {
  return `${API_V1}/applications/${applicationId}`;
}

export const API_ENDPOINTS = {
  applications: {
    collection: `${API_V1}/applications`,
    domains: (applicationId: number) => `${applicationPath(applicationId)}/domains`,
    domainAuthorization: (applicationId: number, domainId: number) =>
      `${applicationPath(applicationId)}/domain-authorizations/${domainId}`,
  },
  domains: {
    collection: `${API_V1}/domains`,
    tenantShared: `${API_V1}/domains/tenant-shared`,
  },
  links: {
    collection: (applicationId?: number) =>
      applicationId == null ? `${API_V1}/links` : `${applicationPath(applicationId)}/links`,
    item: (linkId: number) => `${API_V1}/links/${linkId}`,
    archive: (linkId: number) => `${API_V1}/links/${linkId}/archive`,
    restore: (linkId: number) => `${API_V1}/links/${linkId}/restore`,
    importCsv: (applicationId?: number) =>
      applicationId == null ? `${API_V1}/links/import` : `${applicationPath(applicationId)}/links/import`,
    exportCsv: (applicationId?: number) =>
      applicationId == null ? `${API_V1}/links/export` : `${applicationPath(applicationId)}/links/export`,
  },
  stats: {
    overview: (applicationId?: number) =>
      applicationId == null
        ? `${API_V1}/stats/overview`
        : `${applicationPath(applicationId)}/stats/overview`,
    topLinks: (applicationId?: number) =>
      applicationId == null
        ? `${API_V1}/stats/top-links`
        : `${applicationPath(applicationId)}/stats/top-links`,
    linkDaily: (linkId: number) => `${API_V1}/stats/links/${linkId}/daily`,
  },
  apiKeys: {
    collection: `${API_V1}/api-keys`,
    disable: (id: number) => `${API_V1}/api-keys/${id}/disable`,
    enable: (id: number) => `${API_V1}/api-keys/${id}/enable`,
    rotate: (id: number) => `${API_V1}/api-keys/${id}/rotate`,
  },
  tags: {
    collection: `${API_V1}/tags`,
  },
  approvals: {
    collection: `${API_V1}/approvals`,
    approve: (requestId: number) => `${API_V1}/approvals/${requestId}/approve`,
  },
  auditLogs: {
    collection: `${API_V1}/audit-logs`,
  },
} as const;
