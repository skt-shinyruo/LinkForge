import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DomainDto, LinkDto, PageResponse } from "../services/types";

const listLinksMock = vi.hoisted(() => vi.fn());
const listDomainsForApplicationMock = vi.hoisted(() => vi.fn());
const importLinksCsvMock = vi.hoisted(() => vi.fn());
const updateLinkMock = vi.hoisted(() => vi.fn());

vi.mock("../services/links", () => ({
  archiveLink: vi.fn(),
  createLink: vi.fn(),
  deleteLink: vi.fn(),
  exportLinksCsv: vi.fn(),
  importLinksCsv: importLinksCsvMock,
  listLinks: listLinksMock,
  restoreLink: vi.fn(),
  updateLink: updateLinkMock,
}));

vi.mock("../services/domains", () => ({
  listDomainsForApplication: listDomainsForApplicationMock,
}));

type LinksPagePublic = {
  editForm: { originalUrl: string };
  editingId: { value: number | null };
  error: { value: string | null };
  filters: { showArchived: boolean; keyword: string };
  importResult: { value: { success: number; failed: number; errors: string[] } | null };
  items: { value: LinkDto[] };
  page: { value: number };
  selectedApplicationId: { value: number | null };
  selectedDomainId: { value: number | null };
  size: { value: number };
  total: { value: number };
  availableDomains: { value: DomainDto[] };
  importCsv: () => Promise<void>;
  load: () => Promise<void>;
  saveEdit: () => Promise<void>;
  setArchived: (value: boolean) => Promise<void> | void;
  setImportFile: (file: File | null) => void;
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
    listLinksMock.mockReset();
    listDomainsForApplicationMock.mockReset();
    importLinksCsvMock.mockReset();
    updateLinkMock.mockReset();
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

  it("surfaces pending approval after saving a destination change", async () => {
    listLinksMock.mockResolvedValue(createPageResponse());
    updateLinkMock.mockResolvedValueOnce({
      ...createLink(101),
      pendingApproval: true,
      approvalRequestId: 7001,
    });

    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;
    page.editingId.value = 101;
    page.editForm.originalUrl = "https://example.com/new";

    await page.saveEdit();

    expect(page.editingId.value).toBeNull();
    expect(page.error.value).toBe("目标地址变更已提交审批（#7001），审批通过后生效");
    expect(listLinksMock).toHaveBeenCalledOnce();
  });

  it("imports CSV in the selected application and domain scope", async () => {
    listLinksMock.mockResolvedValue(createPageResponse());
    importLinksCsvMock.mockResolvedValueOnce({ success: 2, failed: 1, errors: ["row 3"] });

    const { useLinksPage } = await import("./useLinksPage");
    const page = useLinksPage() as unknown as LinksPagePublic;
    const file = new File(["originalUrl\nhttps://example.com"], "links.csv");
    page.selectedApplicationId.value = 2001;
    page.selectedDomainId.value = 3001;
    page.setImportFile(file);

    await page.importCsv();

    expect(importLinksCsvMock).toHaveBeenCalledWith(file, {
      applicationId: 2001,
      domainId: 3001,
    });
    expect(page.importResult.value).toEqual({ success: 2, failed: 1, errors: ["row 3"] });
  });
});
