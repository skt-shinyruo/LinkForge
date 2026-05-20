import { API_ENDPOINTS, ensureApiSuccess, withQuery } from "./apiContract";
import { apiFetch } from "./http";
import type {
  DailyStat,
  StatsRangeQuery,
  TopLinkStat,
  TopLinksQuery,
} from "./types";

function buildRangeQuery(range: StatsRangeQuery): Record<string, string> {
  return {
    from: range.from,
    to: range.to,
  };
}

export async function fetchOverviewStats(range: StatsRangeQuery): Promise<DailyStat[]> {
  const path = range.applicationId
    ? API_ENDPOINTS.stats.overview(range.applicationId)
    : API_ENDPOINTS.stats.overview();
  const response = await apiFetch<DailyStat[]>(
    withQuery(path, buildRangeQuery(range), { skipEmptyString: false }),
  );
  return ensureApiSuccess(response, "加载概览失败") ?? [];
}

export async function fetchTopLinksStats(query: TopLinksQuery): Promise<TopLinkStat[]> {
  const path = query.applicationId
    ? API_ENDPOINTS.stats.topLinks(query.applicationId)
    : API_ENDPOINTS.stats.topLinks();
  const response = await apiFetch<TopLinkStat[]>(
    withQuery(
      path,
      {
        ...buildRangeQuery(query),
        limit: query.limit ?? 10,
        sortBy: query.sortBy ?? "pv",
      },
      { skipEmptyString: false },
    ),
  );
  return ensureApiSuccess(response, "加载 Top 报表失败") ?? [];
}

export async function fetchLinkDailyStats(
  linkId: number,
  range: StatsRangeQuery,
): Promise<DailyStat[]> {
  const response = await apiFetch<DailyStat[]>(
    withQuery(API_ENDPOINTS.stats.linkDaily(linkId), buildRangeQuery(range), {
      skipEmptyString: false,
    }),
  );
  return ensureApiSuccess(response, "加载短链统计失败") ?? [];
}
