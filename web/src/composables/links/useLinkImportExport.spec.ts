import { describe, expect, it, vi } from "vitest";
import { ref } from "vue";

const exportLinksCsvMock = vi.hoisted(() => vi.fn());
const importLinksCsvMock = vi.hoisted(() => vi.fn());

vi.mock("../../services/links", () => ({
  exportLinksCsv: exportLinksCsvMock,
  importLinksCsv: importLinksCsvMock,
}));

describe("useLinkImportExport", () => {
  it("passes the current application and domain scope into CSV import", async () => {
    importLinksCsvMock.mockResolvedValueOnce({ success: 1, failed: 0, errors: [] });
    const importFile = ref<File | null>(new File(["originalUrl\nhttps://example.com"], "links.csv"));
    const importing = ref(false);
    const reload = vi.fn().mockResolvedValue(undefined);

    const { useLinkImportExport } = await import("./useLinkImportExport");
    const feature = useLinkImportExport({
      importFile,
      importing,
      setError: vi.fn(),
      getErrorMessage: (error, fallback) => (error instanceof Error ? error.message : fallback),
      getImportQuery: () => ({ applicationId: 2001, domainId: 3001 }),
      getExportQuery: () => ({ applicationId: 2001 }),
      reload,
    });

    await feature.importCsv();

    expect(importLinksCsvMock).toHaveBeenCalledWith(expect.any(File), {
      applicationId: 2001,
      domainId: 3001,
    });
    expect(reload).toHaveBeenCalled();
  });

  it("exposes CSV import result counts and errors after import", async () => {
    importLinksCsvMock.mockResolvedValueOnce({
      success: 2,
      failed: 1,
      errors: ["row 3: invalid URL"],
    });
    const importFile = ref<File | null>(new File(["originalUrl\nhttps://example.com"], "links.csv"));
    const importing = ref(false);

    const { useLinkImportExport } = await import("./useLinkImportExport");
    const feature = useLinkImportExport({
      importFile,
      importing,
      setError: vi.fn(),
      getErrorMessage: (error, fallback) => (error instanceof Error ? error.message : fallback),
      getExportQuery: () => ({}),
      reload: vi.fn().mockResolvedValue(undefined),
    });

    await feature.importCsv();

    expect(feature.importResult.value).toEqual({
      success: 2,
      failed: 1,
      errors: ["row 3: invalid URL"],
    });
  });
});
