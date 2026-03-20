import { apiFetch } from "./http";
import type {
  ApiResponse,
  ApplicationDto,
  CreateApplicationRequest,
} from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

export async function listApplications(): Promise<ApplicationDto[]> {
  const response = await apiFetch<ApplicationDto[]>("/api/v1/applications");
  return ensureApiSuccess(response, "加载应用失败") ?? [];
}

export async function createApplication(
  request: CreateApplicationRequest,
): Promise<ApplicationDto> {
  const response = await apiFetch<ApplicationDto>("/api/v1/applications", {
    method: "POST",
    body: JSON.stringify(request),
  });
  const data = ensureApiSuccess(response, "创建应用失败");
  if (!data) {
    throw new Error("创建应用失败");
  }
  return data;
}
