import type { ApiResponse } from "./types";

export type RuntimeValidator<T> = (value: unknown) => value is T;

const API_V1 = "/api/v1";

type QueryValue = string | number | boolean | null | undefined;

type QueryStringOptions = {
  skipEmptyString?: boolean;
};

/** 检查 HTTP 200 内的业务 code；成功数据允许为 `undefined`。 */
export function ensureApiSuccess<T>(
  response: ApiResponse<T>,
  fallbackMessage: string,
): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

/** 检查业务成功且必须存在 data，适用于后端承诺返回资源的命令和查询。 */
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

/**
 * 使用 `URLSearchParams` 编码查询参数；null/undefined 永不发送，空字符串默认也省略。
 * false 和 0 是有效协议值，不能因 JavaScript truthy 规则被丢弃。
 */
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** 将未知 JSON 解码为统一响应；可选 validator 同时锁定 data 的运行时形状。 */
export function decodeApiResponse<T>(
  value: unknown,
  validateData?: RuntimeValidator<T>,
): ApiResponse<T> {
  if (!isRecord(value) || typeof value.code !== "number" || typeof value.message !== "string") {
    throw new Error("Invalid API response envelope");
  }
  if (value.requestId !== undefined && typeof value.requestId !== "string") {
    throw new Error("Invalid API response requestId");
  }
  if (value.data !== undefined && validateData && !validateData(value.data)) {
    throw new Error("Invalid API response data");
  }
  return value as ApiResponse<T>;
}

/** 解析调用方已获得的原始响应；非空 body 必须符合统一 envelope。 */
export async function parseApiResponse<T>(
  response: Response,
  validateData?: RuntimeValidator<T>,
): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    return {} as ApiResponse<T>;
  }
  return decodeApiResponse(JSON.parse(text) as unknown, validateData);
}

function applicationPath(applicationId: number): string {
  return `${API_V1}/applications/${applicationId}`;
}

/**
 * 控制台端点的单一事实源。
 *
 * application-scoped 链接和统计统一使用 `/applications/{id}/...`；共享域名授权使用
 * `domain-authorizations`。service 不应再内联复制这些路径。
 */
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

/** 前端实际消费的 method/path 清单；与仓库根 contracts/web-api-v1.snapshot.json 对比。 */
export const WEB_API_CONTRACT_ENDPOINTS = [
  ["GET", "/api/v1/me"],
  ["POST", "/api/v1/auth/login"],
  ["POST", "/api/v1/auth/logout"],
  ["GET", "/api/v1/applications"],
  ["POST", "/api/v1/applications"],
  ["GET", "/api/v1/domains"],
  ["GET", "/api/v1/applications/{applicationId}/domains"],
  ["POST", "/api/v1/domains/tenant-shared"],
  ["POST", "/api/v1/applications/{applicationId}/domains"],
  ["POST", "/api/v1/applications/{applicationId}/domain-authorizations/{domainId}"],
  ["GET", "/api/v1/links"],
  ["POST", "/api/v1/links"],
  ["GET", "/api/v1/applications/{applicationId}/links"],
  ["POST", "/api/v1/applications/{applicationId}/links"],
  ["GET", "/api/v1/links/{linkId}"],
  ["PUT", "/api/v1/links/{linkId}"],
  ["DELETE", "/api/v1/links/{linkId}"],
  ["POST", "/api/v1/links/{linkId}/archive"],
  ["POST", "/api/v1/links/{linkId}/restore"],
  ["POST", "/api/v1/links/import"],
  ["POST", "/api/v1/applications/{applicationId}/links/import"],
  ["GET", "/api/v1/links/export"],
  ["GET", "/api/v1/applications/{applicationId}/links/export"],
  ["GET", "/api/v1/stats/overview"],
  ["GET", "/api/v1/applications/{applicationId}/stats/overview"],
  ["GET", "/api/v1/stats/top-links"],
  ["GET", "/api/v1/applications/{applicationId}/stats/top-links"],
  ["GET", "/api/v1/stats/links/{linkId}/daily"],
  ["GET", "/api/v1/api-keys"],
  ["POST", "/api/v1/api-keys"],
  ["PUT", "/api/v1/api-keys/{id}/disable"],
  ["PUT", "/api/v1/api-keys/{id}/enable"],
  ["POST", "/api/v1/api-keys/{id}/rotate"],
  ["GET", "/api/v1/tags"],
  ["POST", "/api/v1/tags"],
  ["GET", "/api/v1/approvals"],
  ["POST", "/api/v1/approvals/{requestId}/approve"],
  ["GET", "/api/v1/audit-logs"],
] as const;
