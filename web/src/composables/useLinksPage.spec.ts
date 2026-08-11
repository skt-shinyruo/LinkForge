import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { DomainDto, LinkDto, PageResponse } from "../services/types";

const listLinksMock = vi.hoisted(() => vi.fn());
const listDomainsForApplicationMock = vi.hoisted(() => vi.fn());
const useLinkImportExportMock = vi.hoisted(() => vi.fn());
const useLinkMutationsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/links", () => ({
  listLinks: listLinksMock,
}));

vi.mock("../services/domains", () => ({
  listDomainsForApplication: listDomainsForApplicationMock,
}));

vi.mock("./links/useLinkImportExport", () => ({
  useLinkImportExport: useLinkImportExportMock,
}));

vi.mock("./links/useLinkMutations", () => ({
  useLinkMutations: useLinkMutationsMock,
}));

type LinksPagePublic = {
  filters: { showArchived: boolean; keyword: string };
  items: { value: LinkDto[] };
  page: { value: number };
  size: { value: number };
  total: { value: number };
  availableDomains: { value: DomainDto[] };
  load: () => Promise<void>;
  setArchived: (value: boolean) => Promise<void> | void;
  setKeyword: (value: string) => void;
  setSelectedApplicationId: (value: number | null) => Promise<void>;
};

function createPageResponse(overrides: Partial<PageResponse<LinkDto>> = {}): PageResponse<LinkDto> {
  return {
    items: [],
    total: 0,
    page: 0,
    size: 50,
    ...overrides,
  };
}

function createLink(id: number): LinkDto {
  return {
    id,
    tenantId: 7,
    code: `code-${id}`,
    shortUrl: `https://lf.test/r/code-${id}`,
    originalUrl: `https://example.com/${id}`,
    enabled: true,
    tags: [],
  };
}

describe("useLinksPage", () => {
  beforeEach(() => {
    vi.resetModules();
    setActivePinia(createPinia());
    listLinksMock.mockReset();
    listDomainsForApplicationMock.mockReset();
    useLinkMutationsMock.mockReset();
    useLinkImportExportMock.mockReset();

    useLinkMutationsMock.mockReturnValue({
      archiveLink: vi.fn(),
      cancelEdit: vi.fn(),
      createLink: vi.fn(),
      deleteLink: vi.fn(),
      restoreLink: vi.fn(),
      saveEdit: vi.fn(),
      startEdit: vi.fn(),
      toggleEnabled: vi.fn(),
    });

    useLinkImportExportMock.mockReturnValue({
      exportCsv: vi.fn(),
      importCsv: vi.fn(),
      importFileName: { value: "" },
      setImportFile: vi.fn(),
    });
  });

  it("requests the selected page instead of hardcoding page=0,size=50", async () => {
    listLinksMock.mockResolvedValueOnce(
      createPageResponse({
        items: [createLink(201)],
        total: 88,
        page: 2,
        size: 25,
      }),
    );

    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;

    page.page.value = 2;
    page.size.value = 25;
    page.filters.keyword = "  promo  ";

    await page.load();

    expect(listLinksMock).toHaveBeenCalledWith(
      {
        page: 2,
        size: 25,
        applicationId: undefined,
        archived: false,
        keyword: "promo",
      },
      { signal: expect.any(AbortSignal) },
    );
    expect(page.items.value).toEqual([createLink(201)]);
    expect(page.total.value).toBe(88);
  });

  it("changing keyword or archived filter resets pagination back to page 0", async () => {
    listLinksMock.mockResolvedValue(createPageResponse());

    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;

    page.page.value = 3;
    page.setKeyword("campaign");

    expect(page.page.value).toBe(0);

    page.page.value = 4;
    await page.setArchived(true);

    expect(page.page.value).toBe(0);
    expect(listLinksMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: 0,
        archived: true,
        keyword: "campaign",
      }),
      { signal: expect.any(AbortSignal) },
    );
  });

  it("keeps the newest response when requests resolve out of order", async () => {
    let resolveFirst!: (value: PageResponse<LinkDto>) => void;
    let resolveSecond!: (value: PageResponse<LinkDto>) => void;
    listLinksMock
      .mockImplementationOnce(
        () => new Promise<PageResponse<LinkDto>>((resolve) => {
          resolveFirst = resolve;
        }),
      )
      .mockImplementationOnce(
        () => new Promise<PageResponse<LinkDto>>((resolve) => {
          resolveSecond = resolve;
        }),
      );

    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;

    page.filters.keyword = "old";
    const firstLoad = page.load();
    page.filters.keyword = "new";
    const secondLoad = page.load();

    const firstSignal = listLinksMock.mock.calls[0]?.[1]?.signal as AbortSignal;
    expect(firstSignal.aborted).toBe(true);

    resolveSecond(createPageResponse({ items: [createLink(2)], total: 1 }));
    await secondLoad;
    resolveFirst(createPageResponse({ items: [createLink(1)], total: 99 }));
    await firstLoad;

    expect(page.items.value).toEqual([createLink(2)]);
    expect(page.total.value).toBe(1);
  });

  it("keeps domains from the newest application when requests resolve out of order", async () => {
    let resolveFirst!: (value: DomainDto[]) => void;
    let resolveSecond!: (value: DomainDto[]) => void;
    listDomainsForApplicationMock
      .mockImplementationOnce(
        () => new Promise<DomainDto[]>((resolve) => {
          resolveFirst = resolve;
        }),
      )
      .mockImplementationOnce(
        () => new Promise<DomainDto[]>((resolve) => {
          resolveSecond = resolve;
        }),
      );
    listLinksMock.mockResolvedValue(createPageResponse());

    const { useAuthStore } = await import("../stores/auth");
    const auth = useAuthStore();
    auth.roles = ["TENANT_ADMIN"];
    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;

    const firstSelection = page.setSelectedApplicationId(11);
    const secondSelection = page.setSelectedApplicationId(22);
    const firstSignal = listDomainsForApplicationMock.mock.calls[0]?.[1]?.signal as AbortSignal;
    expect(firstSignal.aborted).toBe(true);

    resolveSecond([{
      id: 220,
      tenantId: 7,
      applicationId: 22,
      hostname: "new.example.test",
      scope: "APPLICATION_DEDICATED",
    }]);
    await secondSelection;
    resolveFirst([{
      id: 110,
      tenantId: 7,
      applicationId: 11,
      hostname: "old.example.test",
      scope: "APPLICATION_DEDICATED",
    }]);
    await firstSelection;

    expect(page.availableDomains.value.map((domain) => domain.id)).toEqual([220]);
  });
});
