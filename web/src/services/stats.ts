import { apiFetch } from "./http";
import type {
  ApiResponse,
  DailyStat,
  StatsRangeQuery,
  TopLinkStat,
  TopLinksQuery,
} from "./types";

function ensureApiSuccess<T>(response: ApiResponse<T>, fallbackMessage: string): T | undefined {
  if (response.code !== 0) {
    throw new Error(response.message || fallbackMessage);
  }
  return response.data;
}

function buildRangeParams(range: StatsRangeQuery): URLSearchParams {
  const params = new URLSearchParams();
  params.set("from", range.from);
  params.set("to", range.to);
  return params;
}

export async function fetchOverviewStats(range: StatsRangeQuery): Promise<DailyStat[]> {
  const params = buildRangeParams(range);
  const response = await apiFetch<DailyStat[]>(`/api/v1/stats/overview?${params.toString()}`);
  return ensureApiSuccess(response, "加载概览失败") ?? [];
}

export async function fetchTopLinksStats(query: TopLinksQuery): Promise<TopLinkStat[]> {
  const params = buildRangeParams(query);
  params.set("limit", String(query.limit ?? 10));
  params.set("sortBy", query.sortBy ?? "pv");

  const response = await apiFetch<TopLinkStat[]>(
    `/api/v1/stats/top-links?${params.toString()}`,
  );
  return ensureApiSuccess(response, "加载 Top 报表失败") ?? [];
}

export async function fetchLinkDailyStats(
  linkId: number,
  range: StatsRangeQuery,
): Promise<DailyStat[]> {
  const params = buildRangeParams(range);
  const response = await apiFetch<DailyStat[]>(
    `/api/v1/stats/links/${linkId}/daily?${params.toString()}`,
  );
  return ensureApiSuccess(response, "加载短链统计失败") ?? [];
}
