import {
  API_ENDPOINTS,
  ensureApiSuccess,
  parseApiResponse,
  requireApiData,
  withQuery,
} from "./apiContract";
import { apiFetch, authFetch } from "./http";
import type {
  CreateLinkRequest,
  LinkDto,
  LinkExportQuery,
  LinkImportQuery,
  LinkImportResult,
  LinkListQuery,
  PageResponse,
  UpdateLinkRequest,
} from "./types";
import { isLinkDto, isLinkImportResult, pageOf } from "./runtimeContracts";

export async function listLinks(
  query: LinkListQuery = {},
  options: Pick<RequestInit, "signal"> = {},
): Promise<PageResponse<LinkDto>> {
  const page = query.page ?? 0;
  const size = query.size ?? 50;
  const basePath = query.applicationId
    ? API_ENDPOINTS.links.collection(query.applicationId)
    : API_ENDPOINTS.links.collection();
  const response = await apiFetch<PageResponse<LinkDto>>(
    withQuery(basePath, {
      page,
      size,
      archived: query.archived,
      enabled: query.enabled,
      keyword: query.keyword,
      tag: query.tag,
      cursor: query.cursor,
      includeTotal: query.includeTotal,
    }),
    options,
    pageOf(isLinkDto),
  );
  return (
    ensureApiSuccess(response, "加载短链失败") ?? {
      items: [],
      total: 0,
      page,
      size,
      hasMore: false,
      nextCursor: null,
    }
  );
}

export async function createLink(request: CreateLinkRequest): Promise<LinkDto> {
  const path = request.applicationId
    ? API_ENDPOINTS.links.collection(request.applicationId)
    : API_ENDPOINTS.links.collection();
  const response = await apiFetch<LinkDto>(
    path,
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isLinkDto,
  );
  return requireApiData(response, "创建失败");
}

export async function updateLink(
  linkId: number,
  request: UpdateLinkRequest,
): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(
    API_ENDPOINTS.links.item(linkId),
    {
      method: "PUT",
      body: JSON.stringify(request),
    },
    isLinkDto,
  );
  return requireApiData(response, "更新失败");
}

export async function archiveLink(linkId: number): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(
    API_ENDPOINTS.links.archive(linkId),
    { method: "POST" },
    isLinkDto,
  );
  return requireApiData(response, "归档失败");
}

export async function restoreLink(linkId: number): Promise<LinkDto> {
  const response = await apiFetch<LinkDto>(
    API_ENDPOINTS.links.restore(linkId),
    { method: "POST" },
    isLinkDto,
  );
  return requireApiData(response, "恢复失败");
}

export async function deleteLink(linkId: number): Promise<void> {
  const response = await apiFetch<void>(API_ENDPOINTS.links.item(linkId), {
    method: "DELETE",
  });
  ensureApiSuccess(response, "删除失败");
}

export async function importLinksCsv(
  file: File,
  query: LinkImportQuery = {},
): Promise<LinkImportResult> {
  const applicationId = query.applicationId;
  if (applicationId != null && query.domainId == null) {
    throw new Error("请选择应用域名");
  }

  const formData = new FormData();
  formData.append("file", file);

  const path = applicationId != null
    ? withQuery(API_ENDPOINTS.links.importCsv(applicationId), {
        domainId: query.domainId,
      })
    : API_ENDPOINTS.links.importCsv();

  const response = await authFetch(path, {
    method: "POST",
    body: formData,
  });
  const payload = await parseApiResponse<LinkImportResult>(response, isLinkImportResult);

  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || `导入失败（HTTP ${response.status}）`);
  }

  return payload.data ?? { success: 0, failed: 0, errors: [] };
}

export async function exportLinksCsv(query: LinkExportQuery = {}): Promise<Blob> {
  const page = query.page ?? 0;
  const size = query.size ?? 1000;
  const basePath = query.applicationId
    ? API_ENDPOINTS.links.exportCsv(query.applicationId)
    : API_ENDPOINTS.links.exportCsv();
  const response = await authFetch(
    withQuery(basePath, {
      page,
      size,
      archived: query.archived,
      enabled: query.enabled,
      keyword: query.keyword,
      tag: query.tag,
    }),
  );

  if (!response.ok) {
    throw new Error(`导出失败（HTTP ${response.status}）`);
  }

  return response.blob();
}
