import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { createApp, h, type App } from "vue";
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

function createLinkPage(page: number, items: LinkDto[], total: number): PageResponse<LinkDto> {
  return {
    items,
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

describe("useStatsPage", () => {
  let app: App<Element> | null = null;
  let pinia: ReturnType<typeof createPinia>;

  beforeEach(() => {
    vi.resetModules();
    vi.restoreAllMocks();
    pinia = createPinia();
    setActivePinia(pinia);
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

  afterEach(() => {
    app?.unmount();
    app = null;
    document.body.innerHTML = "";
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

  it("keeps all applications selected when admin stats page first loads", async () => {
    listApplicationsMock.mockResolvedValue([createApplication(11), createApplication(12)]);
    listLinksMock.mockResolvedValue(createLinkPage(0, [createLink(101)], 1));

    const { useAuthStore } = await import("../stores/auth");
    const { useStatsPage } = await import("./useStatsPage");
    const auth = useAuthStore();
    auth.roles = ["TENANT_ADMIN"];

    let page!: ReturnType<typeof useStatsPage>;
    const host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp({
      setup() {
        page = useStatsPage();
        return () => h("div");
      },
    });
    app.use(pinia);
    app.mount(host);

    await flushPromises();

    expect(listApplicationsMock).toHaveBeenCalledTimes(1);
    expect(page.selectedApplicationId.value).toBeNull();
    expect(listLinksMock).toHaveBeenCalledWith({
      applicationId: undefined,
      page: 0,
      size: 100,
    });
    expect(fetchOverviewStatsMock).toHaveBeenCalledWith(expect.objectContaining({ applicationId: undefined }));
    expect(fetchTopLinksStatsMock).toHaveBeenCalledWith(expect.objectContaining({ applicationId: undefined }));
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
