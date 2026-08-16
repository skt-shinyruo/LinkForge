import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApplicationDto, DailyStat, LinkDto, PageResponse, TopLinkStat } from "../services/types";

const listApplicationsMock = vi.hoisted(() => vi.fn());
const listLinksMock = vi.hoisted(() => vi.fn());
const fetchLinkDailyStatsMock = vi.hoisted(() => vi.fn());
const fetchOverviewStatsMock = vi.hoisted(() => vi.fn());
const fetchTopLinksStatsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/applications", () => ({
  listApplications: listApplicationsMock,
}));

vi.mock("../services/links", () => ({
  listLinks: listLinksMock,
}));

vi.mock("../services/stats", () => ({
  fetchLinkDailyStats: fetchLinkDailyStatsMock,
  fetchOverviewStats: fetchOverviewStatsMock,
  fetchTopLinksStats: fetchTopLinksStatsMock,
}));

function createApplication(id: number): ApplicationDto {
  return {
    id,
    tenantId: 9,
    applicationKey: `app-${id}`,
    displayName: `App ${id}`,
  };
}

function createLink(id: number): LinkDto {
  return {
    id,
    tenantId: 9,
    code: `stats-${id}`,
    shortUrl: `https://lf.test/r/stats-${id}`,
    originalUrl: `https://example.com/stats/${id}`,
    enabled: true,
    tags: [],
  };
}

function createLinkPage(
  page: number,
  items: LinkDto[],
  total: number,
  nextCursor: string | null = null,
): PageResponse<LinkDto> {
  return {
    hasMore: nextCursor != null,
    items,
    nextCursor,
    total,
    page,
    size: items.length || 2,
  };
}

function flushPromises() {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, 0);
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe("useStatsPage", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    listApplicationsMock.mockReset();
    listLinksMock.mockReset();
    fetchLinkDailyStatsMock.mockReset();
    fetchOverviewStatsMock.mockReset();
    fetchTopLinksStatsMock.mockReset();

    listApplicationsMock.mockResolvedValue([] satisfies ApplicationDto[]);
    fetchOverviewStatsMock.mockResolvedValue([] satisfies DailyStat[]);
    fetchTopLinksStatsMock.mockResolvedValue([] satisfies TopLinkStat[]);
    fetchLinkDailyStatsMock.mockResolvedValue([] satisfies DailyStat[]);
  });

  it("loads one bounded cursor page of link options without requesting a total", async () => {
    listLinksMock.mockResolvedValueOnce(
      createLinkPage(0, [createLink(1), createLink(2)], -1, "next-page"),
    );

    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    await page.searchLinks();

    expect(listLinksMock).toHaveBeenCalledOnce();
    expect(listLinksMock).toHaveBeenCalledWith(
      {
        applicationId: undefined,
        cursor: undefined,
        includeTotal: false,
        keyword: undefined,
        size: 20,
      },
      { signal: expect.any(AbortSignal) },
    );
    expect(page.links.value.map((link) => link.id)).toEqual([1, 2]);
    expect(page.linkOptionsHasMore.value).toBe(true);
    expect(page.selectedLinkId.value).toBe(1);
  });

  it("searches and appends link options with the backend cursor", async () => {
    listLinksMock
      .mockResolvedValueOnce(createLinkPage(0, [createLink(3)], -1, "cursor-3"))
      .mockResolvedValueOnce(createLinkPage(0, [createLink(4)], -1));

    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();
    page.linkSearch.value = "campaign";

    await page.searchLinks();
    await page.loadMoreLinks();

    expect(listLinksMock.mock.calls.map(([query]) => query)).toEqual([
      {
        applicationId: undefined,
        cursor: undefined,
        includeTotal: false,
        keyword: "campaign",
        size: 20,
      },
      {
        applicationId: undefined,
        cursor: "cursor-3",
        includeTotal: false,
        keyword: "campaign",
        size: 20,
      },
    ]);
    expect(page.links.value.map((link) => link.id)).toEqual([3, 4]);
    expect(page.linkOptionsHasMore.value).toBe(false);
  });

  it("refreshes reports without reloading link options", async () => {
    listLinksMock.mockResolvedValueOnce(createLinkPage(0, [createLink(5)], -1));
    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();
    await page.searchLinks();
    listLinksMock.mockClear();

    await page.refresh();

    expect(listLinksMock).not.toHaveBeenCalled();
    expect(fetchOverviewStatsMock).toHaveBeenCalledOnce();
    expect(fetchTopLinksStatsMock).toHaveBeenCalledOnce();
    expect(fetchLinkDailyStatsMock).toHaveBeenCalledWith(
      5,
      expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
      { signal: expect.any(AbortSignal) },
    );
  });

  it("refreshes only the selected link trend when the selection changes", async () => {
    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    await page.onSelectedLinkChange(7);

    expect(fetchLinkDailyStatsMock).toHaveBeenCalledOnce();
    expect(fetchLinkDailyStatsMock).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
      { signal: expect.any(AbortSignal) },
    );
    expect(fetchOverviewStatsMock).not.toHaveBeenCalled();
    expect(fetchTopLinksStatsMock).not.toHaveBeenCalled();
  });

  it("clears link-scoped state when the next application's link options fail", async () => {
    const applicationA = 11;
    const applicationB = 12;
    const linkA = createLink(101);
    const applicationATrend = [{ day: "2026-08-15", pv: 11, uv: 10 }];
    const applicationBOverview = [{ day: "2026-08-16", pv: 22, uv: 20 }];
    const applicationBTopLinks = [{
      linkId: 202,
      code: "application-b",
      shortUrl: null,
      originalUrl: null,
      pv: 22,
      uv: 20,
      deleted: false,
    }];
    listLinksMock
      .mockResolvedValueOnce(createLinkPage(0, [linkA], 1))
      .mockRejectedValueOnce(new Error("Application B links unavailable"));
    fetchLinkDailyStatsMock.mockResolvedValue(applicationATrend);
    fetchOverviewStatsMock
      .mockResolvedValueOnce([{ day: "2026-08-15", pv: 11, uv: 10 }])
      .mockResolvedValueOnce(applicationBOverview);
    fetchTopLinksStatsMock
      .mockResolvedValueOnce([] satisfies TopLinkStat[])
      .mockResolvedValueOnce(applicationBTopLinks);

    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();
    await page.setSelectedApplicationId(applicationA);

    expect(page.links.value).toEqual([linkA]);
    expect(page.selectedLinkId.value).toBe(linkA.id);
    expect(page.linkStats.value).toEqual(applicationATrend);
    fetchLinkDailyStatsMock.mockClear();

    await page.setSelectedApplicationId(applicationB);

    expect(fetchLinkDailyStatsMock).not.toHaveBeenCalled();
    expect(page.links.value).toEqual([]);
    expect(page.selectedLinkId.value).toBeNull();
    expect(page.linkStats.value).toEqual([]);
    expect(page.linkOptionsError.value).toBe("Application B links unavailable");
    expect(page.overviewStats.value).toEqual(applicationBOverview);
    expect(page.topLinks.value).toEqual(applicationBTopLinks);
    expect(fetchOverviewStatsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ applicationId: applicationB }),
      { signal: expect.any(AbortSignal) },
    );
    expect(fetchTopLinksStatsMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ applicationId: applicationB }),
      { signal: expect.any(AbortSignal) },
    );
  });

  it("prevents an older link search from replacing newer options", async () => {
    const older = deferred<PageResponse<LinkDto>>();
    const newer = deferred<PageResponse<LinkDto>>();
    listLinksMock
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise);
    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    page.linkSearch.value = "older";
    const olderSearch = page.searchLinks();
    page.linkSearch.value = "newer";
    const newerSearch = page.searchLinks();

    newer.resolve(createLinkPage(0, [createLink(22)], -1));
    await newerSearch;
    older.resolve(createLinkPage(0, [createLink(11)], -1));
    await olderSearch;

    expect(page.links.value.map((link) => link.id)).toEqual([22]);
    expect(page.selectedLinkId.value).toBe(22);
  });

  it("prevents an older selected-link trend from replacing the latest selection", async () => {
    const older = deferred<DailyStat[]>();
    const newer = deferred<DailyStat[]>();
    fetchLinkDailyStatsMock
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise);
    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    const olderSelection = page.onSelectedLinkChange(11);
    const newerSelection = page.onSelectedLinkChange(22);
    newer.resolve([{ day: "2026-08-16", pv: 22, uv: 20 }]);
    await newerSelection;
    older.resolve([{ day: "2026-08-15", pv: 11, uv: 10 }]);
    await olderSelection;

    expect(page.selectedLinkId.value).toBe(22);
    expect(page.linkStats.value).toEqual([{ day: "2026-08-16", pv: 22, uv: 20 }]);
  });

  it("prevents an older Top response from overwriting a newer range refresh", async () => {
    const older = deferred<TopLinkStat[]>();
    const newer = deferred<TopLinkStat[]>();
    fetchTopLinksStatsMock
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise);
    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    page.setTopSortBy("uv");
    page.setRange(30);
    newer.resolve([{
      linkId: 2,
      code: "newer",
      shortUrl: null,
      originalUrl: null,
      pv: 2,
      uv: 2,
      deleted: false,
    }]);
    await flushPromises();
    older.resolve([{
      linkId: 1,
      code: "older",
      shortUrl: null,
      originalUrl: null,
      pv: 1,
      uv: 1,
      deleted: false,
    }]);
    await flushPromises();

    expect(page.topLinks.value.map((item) => item.linkId)).toEqual([2]);
  });

  it("keeps all applications selected when admin stats page first loads", async () => {
    listApplicationsMock.mockResolvedValue([createApplication(11), createApplication(12)]);
    listLinksMock.mockResolvedValue(createLinkPage(0, [createLink(101)], 1));

    const { useAuthStore } = await import("../stores/auth");
    const { useStatsPage } = await import("./useStatsPage");
    const auth = useAuthStore();
    auth.roles = ["TENANT_ADMIN"];

    const page = useStatsPage();
    await page.init();

    expect(listApplicationsMock).toHaveBeenCalledTimes(1);
    expect(page.selectedApplicationId.value).toBeNull();
    expect(listLinksMock).toHaveBeenCalledWith(
      {
        applicationId: undefined,
        cursor: undefined,
        includeTotal: false,
        keyword: undefined,
        size: 20,
      },
      { signal: expect.any(AbortSignal) },
    );
    expect(fetchOverviewStatsMock).toHaveBeenCalledWith(
      expect.objectContaining({ applicationId: undefined }),
      { signal: expect.any(AbortSignal) },
    );
    expect(fetchTopLinksStatsMock).toHaveBeenCalledWith(
      expect.objectContaining({ applicationId: undefined }),
      { signal: expect.any(AbortSignal) },
    );
    expect(fetchLinkDailyStatsMock).toHaveBeenCalledTimes(1);
  });

  it("does not load tenant application options for platform admins", async () => {
    listLinksMock.mockResolvedValue(createLinkPage(0, [createLink(201)], 1));

    const { useAuthStore } = await import("../stores/auth");
    const { useStatsPage } = await import("./useStatsPage");
    const auth = useAuthStore();
    auth.roles = ["PLATFORM_ADMIN"];

    const page = useStatsPage();
    await page.init();

    expect(listApplicationsMock).not.toHaveBeenCalled();
    expect(listLinksMock).toHaveBeenCalledWith(
      {
        applicationId: undefined,
        cursor: undefined,
        includeTotal: false,
        keyword: undefined,
        size: 20,
      },
      { signal: expect.any(AbortSignal) },
    );
  });

  it("copies the published short URL without rebuilding it from the console origin", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });

    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    await page.copyShort("https://go.example.test/r/stats-1");

    expect(writeText).toHaveBeenCalledWith("https://go.example.test/r/stats-1");
  });
});
