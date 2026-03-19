import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DailyStat, LinkDto, PageResponse, TopLinkStat } from "../services/types";

const listLinksMock = vi.hoisted(() => vi.fn());
const fetchLinkDailyStatsMock = vi.hoisted(() => vi.fn());
const fetchOverviewStatsMock = vi.hoisted(() => vi.fn());
const fetchTopLinksStatsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/links", () => ({
  listLinks: listLinksMock,
}));

vi.mock("../services/stats", () => ({
  fetchLinkDailyStats: fetchLinkDailyStatsMock,
  fetchOverviewStats: fetchOverviewStatsMock,
  fetchTopLinksStats: fetchTopLinksStatsMock,
}));

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

function createLinkPage(page: number, items: LinkDto[], total: number): PageResponse<LinkDto> {
  return {
    items,
    total,
    page,
    size: items.length || 2,
  };
}

describe("useStatsPage", () => {
  beforeEach(() => {
    vi.resetModules();
    listLinksMock.mockReset();
    fetchLinkDailyStatsMock.mockReset();
    fetchOverviewStatsMock.mockReset();
    fetchTopLinksStatsMock.mockReset();

    fetchOverviewStatsMock.mockResolvedValue([] satisfies DailyStat[]);
    fetchTopLinksStatsMock.mockResolvedValue([] satisfies TopLinkStat[]);
    fetchLinkDailyStatsMock.mockResolvedValue([] satisfies DailyStat[]);
  });

  it("can load link options beyond the first backend page", async () => {
    listLinksMock
      .mockResolvedValueOnce(createLinkPage(0, [createLink(1), createLink(2)], 5))
      .mockResolvedValueOnce(createLinkPage(1, [createLink(3), createLink(4)], 5))
      .mockResolvedValueOnce(createLinkPage(2, [createLink(5)], 5));

    const { useStatsPage } = await import("./useStatsPage");
    const page = useStatsPage();

    await page.refresh();

    expect(listLinksMock.mock.calls.map(([query]) => query.page)).toEqual([0, 1, 2]);
    expect(page.links.value.map((link) => link.id)).toEqual([1, 2, 3, 4, 5]);
    expect(page.selectedLinkId.value).toBe(1);
  });
});
