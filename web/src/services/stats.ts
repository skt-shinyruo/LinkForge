import { API_ENDPOINTS, ensureApiSuccess, withQuery } from "./apiContract";
import { apiFetch } from "./http";
import type {
  DailyStat,
  StatsRangeQuery,
  TopLinkStat,
  TopLinksQuery,
} from "./types";
import { arrayOf, isDailyStat, isTopLinkStat } from "./runtimeContracts";

function buildRangeQuery(range: StatsRangeQuery): Record<string, string> {
  return {
    from: range.from,
    to: range.to,
  };
}

export async function fetchOverviewStats(
  range: StatsRangeQuery,
  options: Pick<RequestInit, "signal"> = {},
): Promise<DailyStat[]> {
  const response = await apiFetch<DailyStat[]>(
    withQuery(API_ENDPOINTS.stats.overview(range.applicationId), buildRangeQuery(range)),
    options,
    arrayOf(isDailyStat),
  );
  return ensureApiSuccess(response, "加载概览失败") ?? [];
}

export async function fetchTopLinksStats(
  query: TopLinksQuery,
  options: Pick<RequestInit, "signal"> = {},
): Promise<TopLinkStat[]> {
  const response = await apiFetch<TopLinkStat[]>(
    withQuery(
      API_ENDPOINTS.stats.topLinks(query.applicationId),
      {
        ...buildRangeQuery(query),
        limit: query.limit ?? 10,
        sortBy: query.sortBy ?? "pv",
      },
    ),
    options,
    arrayOf(isTopLinkStat),
  );
  return ensureApiSuccess(response, "加载 Top 报表失败") ?? [];
}

export async function fetchLinkDailyStats(
  linkId: number,
  range: StatsRangeQuery,
  options: Pick<RequestInit, "signal"> = {},
): Promise<DailyStat[]> {
  const response = await apiFetch<DailyStat[]>(
    withQuery(API_ENDPOINTS.stats.linkDaily(linkId), buildRangeQuery(range)),
    options,
    arrayOf(isDailyStat),
  );
  return ensureApiSuccess(response, "加载短链统计失败") ?? [];
}
