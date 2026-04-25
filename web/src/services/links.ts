import { apiFetch, authFetch } from "./http";
import type {
  ApiResponse,
  CreateLinkRequest,
  LinkDto,
  LinkExportQuery,
  LinkImportQuery,
  LinkImportResult,
  LinkListQuery,
  PageResponse,
  UpdateLinkRequest,
} from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

function requireApiData<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  const data = ensureApiSuccess(response, fallbackMessage);
  if (data === undefined) {
    throw new Error(fallbackMessage);
  }
  return data;
}

function buildQueryString(
  values: Record<string, string | number | boolean | undefined>,
): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== "") {
      params.set(key, String(value));
    }
  }
  return params.toString();
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    return {} as ApiResponse<T>;
  }
  return JSON.parse(text) as ApiResponse<T>;
}

export async function listLinks(
  query: LinkListQuery = {},
): Promise<PageResponse<LinkDto>> {
  const page = query.page ?? 0;
  const size = query.size ?? 50;
  const queryString = buildQueryString({
    page,
    size,
    archived: query.archived,
    enabled: query.enabled,
    keyword: query.keyword,
    tag: query.tag,
  });
  const basePath = query.applicationId
    ? `/api/v1/applications/${query.applicationId}/links`
    : "/api/v1/links";
  const response = await apiFetch<PageResponse<LinkDto>>(
    `${basePath}${queryString ? `?${queryString}` : ""}`,
  );
  return (
    ensureApiSuccess(response, "加载短链失败") ?? {
      items: [],
      total: 0,
      page,
      size,
    }
  );
}

export async function createLink(request: CreateLinkRequest): Promise<LinkDto> {
  const path = request.applicationId
    ? `/api/v1/applications/${request.applicationId}/links`
    : "/api/v1/links";
  const response = await apiFetch<LinkDto>(path, {
    method: "POST",
    body: JSON.stringify(request),
  });
  return requireApiData(response, "创建失败");
}

export async function updateLink(
  linkId: number,
  request: UpdateLinkRequest,
): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(`/api/v1/links/${linkId}`, {
    method: "PUT",
    body: JSON.stringify(request),
  });
  return requireApiData(response, "更新失败");
}

export async function archiveLink(linkId: number): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(`/api/v1/links/${linkId}/archive`, {
    method: "POST",
  });
  return requireApiData(response, "归档失败");
}

export async function restoreLink(linkId: number): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(`/api/v1/links/${linkId}/restore`, {
    method: "POST",
  });
  return requireApiData(response, "恢复失败");
}

export async function deleteLink(linkId: number): Promise<void> {
  const response = await apiFetch<void>(`/api/v1/links/${linkId}`, {
    method: "DELETE",
  });
  ensureApiSuccess(response, "删除失败");
}

export async function importLinksCsv(
  file: File,
  query: LinkImportQuery = {},
): Promise<LinkImportResult> {
  const scoped = query.applicationId != null;
  if (scoped && query.domainId == null) {
    throw new Error("请选择应用域名");
  }

  const formData = new FormData();
  formData.append("file", file);

  const path = scoped
    ? `/api/v1/applications/${query.applicationId}/links/import?${buildQueryString({
        domainId: query.domainId,
      })}`
    : "/api/v1/links/import";

  const response = await authFetch(path, {
    method: "POST",
    body: formData,
  });
  const payload = await parseApiResponse<LinkImportResult>(response);

  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || `导入失败（HTTP ${response.status}）`);
  }

  return payload.data ?? { success: 0, failed: 0, errors: [] };
}

export async function exportLinksCsv(query: LinkExportQuery = {}): Promise<Blob> {
  const page = query.page ?? 0;
  const size = query.size ?? 1000;
  const queryString = buildQueryString({
    page,
    size,
    archived: query.archived,
    enabled: query.enabled,
    keyword: query.keyword,
    tag: query.tag,
  });
  const basePath = query.applicationId
    ? `/api/v1/applications/${query.applicationId}/links/export`
    : "/api/v1/links/export";
  const response = await authFetch(`${basePath}${queryString ? `?${queryString}` : ""}`);

  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }

  return response.blob();
}
