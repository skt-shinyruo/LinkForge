import { apiFetch } from "./http";
import type { ApiResponse, CreateTagRequest, TagDto } from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listTags(): Promise<TagDto[]> {
  const response = await apiFetch<TagDto[]>("/api/v1/tags");
  return ensureApiSuccess(response, "加载标签失败") ?? [];
}

export async function createTag(request: CreateTagRequest): Promise<TagDto> {
  const response = await apiFetch<TagDto>("/api/v1/tags", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "创建失败");
  if (!data) {
    throw new Error("创建失败");
  }
  return data;
}
