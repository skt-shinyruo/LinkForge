import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { LinkDto, PageResponse } from "../services/types";

const listLinksMock = vi.hoisted(() => vi.fn());
const useLinkImportExportMock = vi.hoisted(() => vi.fn());
const useLinkMutationsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/links", () => ({
  listLinks: listLinksMock,
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
  load: () => Promise<void>;
  setArchived: (value: boolean) => Promise<void> | void;
  setKeyword: (value: string) => void;
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

    expect(listLinksMock).toHaveBeenCalledWith({
      page: 2,
      size: 25,
      archived: false,
      keyword: "promo",
    });
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
    );
  });
});
