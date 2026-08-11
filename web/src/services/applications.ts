import { API_ENDPOINTS, ensureApiSuccess, requireApiData } from "./apiContract";
import { apiFetch } from "./http";
import type {
  ApplicationDto,
  CreateApplicationRequest,
} from "./types";
import { arrayOf, isApplicationDto } from "./runtimeContracts";

export async function listApplications(): Promise<ApplicationDto[]> {
  const response = await apiFetch<ApplicationDto[]>(
    API_ENDPOINTS.applications.collection,
    {},
    arrayOf(isApplicationDto),
  );
  return ensureApiSuccess(response, "加载应用失败") ?? [];
}

export async function createApplication(
  request: CreateApplicationRequest,
): Promise<ApplicationDto> {
  const response = await apiFetch<ApplicationDto>(
    API_ENDPOINTS.applications.collection,
    {
      method: "POST",
      body: JSON.stringify(request),
    },
    isApplicationDto,
  );
  return requireApiData(response, "创建应用失败");
}
