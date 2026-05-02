import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiKeyDto, ApplicationDto, DailyStat, DomainDto, TopLinkStat } from "../services/types";

const listApplicationsMock = vi.hoisted(() => vi.fn());
const listApiKeysMock = vi.hoisted(() => vi.fn());
const listDomainsForApplicationMock = vi.hoisted(() => vi.fn());
const fetchOverviewStatsMock = vi.hoisted(() => vi.fn());
const fetchTopLinksStatsMock = vi.hoisted(() => vi.fn());
const routeMock = vi.hoisted(() => ({
  params: {
    applicationId: "42",
  },
}));

vi.mock("vue-router", () => ({
  useRoute: () => routeMock,
}));

vi.mock("../services/applications", () => ({
  listApplications: listApplicationsMock,
}));

vi.mock("../services/apiKeys", () => ({
  listApiKeys: listApiKeysMock,
}));

vi.mock("../services/domains", () => ({
  listDomainsForApplication: listDomainsForApplicationMock,
}));

vi.mock("../services/stats", () => ({
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

function createApiKey(id: number): ApiKeyDto {
  return {
    id,
    applicationId: 42,
    name: `key-${id}`,
    status: "ACTIVE",
  };
}

function createDomain(id: number): DomainDto {
  return {
    id,
    tenantId: 9,
    applicationId: 42,
    hostname: `d-${id}.example.test`,
    scope: "APPLICATION_DEDICATED",
  };
}

function createTopLink(id: number): TopLinkStat {
  return {
    linkId: id,
    code: `code-${id}`,
    shortUrl: `https://lf.test/r/code-${id}`,
    originalUrl: `https://example.com/${id}`,
    pv: id * 10,
    uv: id,
    deleted: false,
  };
}

describe("useApplicationDetailPage", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useRealTimers();
    routeMock.params.applicationId = "42";
    listApplicationsMock.mockReset();
    listApiKeysMock.mockReset();
    listDomainsForApplicationMock.mockReset();
    fetchOverviewStatsMock.mockReset();
    fetchTopLinksStatsMock.mockReset();
  });

  it("loads detail data for the routed application and aggregates recent PV", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-05-02T12:00:00Z"));

    const apiKeys = [createApiKey(1)];
    const domains = [createDomain(1), createDomain(2)];
    const topLinks = [createTopLink(101)];
    const overview = [
      { day: "2026-05-01", pv: 11, uv: 5 },
      { day: "2026-05-02", pv: 17, uv: 9 },
    ] satisfies DailyStat[];

    listApplicationsMock.mockResolvedValue([createApplication(1), createApplication(42)]);
    listApiKeysMock.mockResolvedValue(apiKeys);
    listDomainsForApplicationMock.mockResolvedValue(domains);
    fetchOverviewStatsMock.mockResolvedValue(overview);
    fetchTopLinksStatsMock.mockResolvedValue(topLinks);

    const { useApplicationDetailPage } = await import("./useApplicationDetailPage");
    const page = useApplicationDetailPage();

    await page.load();

    expect(listApiKeysMock).toHaveBeenCalledWith(42);
    expect(listDomainsForApplicationMock).toHaveBeenCalledWith(42);
    expect(fetchOverviewStatsMock).toHaveBeenCalledWith({
      from: "2026-04-26",
      to: "2026-05-02",
      applicationId: 42,
    });
    expect(fetchTopLinksStatsMock).toHaveBeenCalledWith({
      from: "2026-04-26",
      to: "2026-05-02",
      applicationId: 42,
      limit: 5,
      sortBy: "pv",
    });
    expect(page.application.value?.id).toBe(42);
    expect(page.applicationId.value).toBe(42);
    expect(page.apiKeys.value).toEqual(apiKeys);
    expect(page.domains.value).toEqual(domains);
    expect(page.recentPv.value).toBe(28);
    expect(page.topLinks.value).toEqual(topLinks);
    expect(page.loading.value).toBe(false);
    expect(page.error.value).toBeNull();
  });

  it("stores a readable error when detail loading fails", async () => {
    listApplicationsMock.mockRejectedValue(new Error("backend unavailable"));
    listApiKeysMock.mockResolvedValue([] satisfies ApiKeyDto[]);
    listDomainsForApplicationMock.mockResolvedValue([] satisfies DomainDto[]);
    fetchOverviewStatsMock.mockResolvedValue([] satisfies DailyStat[]);
    fetchTopLinksStatsMock.mockResolvedValue([] satisfies TopLinkStat[]);

    const { useApplicationDetailPage } = await import("./useApplicationDetailPage");
    const page = useApplicationDetailPage();

    await page.load();

    expect(page.error.value).toBe("backend unavailable");
    expect(page.loading.value).toBe(false);
  });
});
