import { API_ENDPOINTS, ensureApiSuccess, requireApiData } from "./apiContract";
import { apiFetch } from "./http";
import type { CreateTagRequest, TagDto } from "./types";
import { arrayOf, isTagDto } from "./runtimeContracts";

export async function listTags(): Promise<TagDto[]> {
  const response = await apiFetch<TagDto[]>(
    API_ENDPOINTS.tags.collection,
    {},
    arrayOf(isTagDto),
  );
  return ensureApiSuccess(response, "加载标签失败") ?? [];
}

export async function createTag(request: CreateTagRequest): Promise<TagDto> {
  const response = await apiFetch<TagDto>(
    API_ENDPOINTS.tags.collection,
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isTagDto,
  );
  return requireApiData(response, "创建失败");
}
