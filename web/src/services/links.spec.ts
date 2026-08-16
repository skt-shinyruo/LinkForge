import { beforeEach, describe, expect, it, vi } from "vitest";

const apiFetchMock = vi.hoisted(() => vi.fn());
const authFetchMock = vi.hoisted(() => vi.fn());

vi.mock("./http", () => ({
  apiFetch: apiFetchMock,
  authFetch: authFetchMock,
}));

describe("link CSV service", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    authFetchMock.mockReset();
  });

  it("imports CSV through the selected application and domain scope", async () => {
    apiFetchMock.mockResolvedValueOnce({
      code: 0,
      message: "ok",
      data: { success: 1, failed: 0, errors: [] },
    });

    const { importLinksCsv } = await import("./links");
    const file = new File(["originalUrl\nhttps://example.com"], "links.csv", { type: "text/csv" });

    const result = await importLinksCsv(file, { applicationId: 2001, domainId: 3001 });

    expect(result).toEqual({ success: 1, failed: 0, errors: [] });
    expect(apiFetchMock).toHaveBeenCalledWith(
      "/api/v1/applications/2001/links/import?domainId=3001",
      expect.objectContaining({
        method: "POST",
        body: expect.any(FormData),
      }),
      expect.any(Function),
    );
  });
});
