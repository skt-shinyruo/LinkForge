import { API_ENDPOINTS, ensureApiSuccess, requireApiData } from "./apiContract";
import { apiFetch } from "./http";
import type { CreateTagRequest, TagDto } from "./types";

export async function listTags(): Promise<TagDto[]> {
  const response = await apiFetch<TagDto[]>(API_ENDPOINTS.tags.collection);
  return ensureApiSuccess(response, "加载标签失败") ?? [];
}

export async function createTag(request: CreateTagRequest): Promise<TagDto> {
  const response = await apiFetch<TagDto>(API_ENDPOINTS.tags.collection, {
    method: "POST",
    body: JSON.stringify(request),
  });
  return requireApiData(response, "创建失败");
}
