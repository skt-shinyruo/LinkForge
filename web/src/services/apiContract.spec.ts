import { describe, expect, it } from "vitest";
import {
  API_ENDPOINTS,
  ensureApiSuccess,
  requireApiData,
  withQuery,
} from "./apiContract";
import { isLinkDto, pageOf } from "./runtimeContracts";

describe("service API contract helpers", () => {
  it("unwraps successful ApiResponse payloads and preserves empty success data", () => {
    expect(
      ensureApiSuccess({ code: 0, message: "ok", data: { id: 1 } }, "fallback"),
    ).toEqual({ id: 1 });
    expect(ensureApiSuccess({ code: 0, message: "ok" }, "fallback")).toBeUndefined();
  });

  it("throws backend messages before fallback messages for failed ApiResponse payloads", () => {
    expect(() =>
      ensureApiSuccess({ code: 40001, message: "backend failed" }, "fallback"),
    ).toThrow("backend failed");
    expect(() =>
      ensureApiSuccess({ code: 40001, message: "" }, "fallback"),
    ).toThrow("fallback");
  });

  it("requires data only after the ApiResponse is successful", () => {
    expect(
      requireApiData({ code: 0, message: "ok", data: "value" }, "missing"),
    ).toBe("value");
    expect(() => requireApiData<string>({ code: 0, message: "ok" }, "missing")).toThrow(
      "missing",
    );
  });

  it("builds query strings while omitting undefined and empty-string values", () => {
    expect(
      withQuery("/api/v1/links", {
        page: 0,
        size: 50,
        enabled: false,
        keyword: "",
        missing: undefined,
        tag: "中文",
      }),
    ).toBe("/api/v1/links?page=0&size=50&enabled=false&tag=%E4%B8%AD%E6%96%87");
  });

  it("attaches query strings only when at least one parameter is present", () => {
    expect(withQuery("/api/v1/links", { page: 0, keyword: "" })).toBe(
      "/api/v1/links?page=0",
    );
    expect(withQuery("/api/v1/links", { keyword: "" })).toBe("/api/v1/links");
  });

  it("rejects malformed DTO data instead of trusting a TypeScript assertion", () => {
    expect(isLinkDto({ id: 1, tenantId: 2, code: "x" })).toBe(false);
    expect(pageOf(isLinkDto)({ items: [], total: 0, page: 0, size: 20 })).toBe(true);
    expect(pageOf(isLinkDto)({ items: [{ id: 1 }], total: 1, page: 0, size: 20 })).toBe(false);
  });

  it("centralizes endpoint builders for tenant and application scoped routes", () => {
    expect(API_ENDPOINTS.links.collection()).toBe("/api/v1/links");
    expect(API_ENDPOINTS.links.collection(2001)).toBe(
      "/api/v1/applications/2001/links",
    );
    expect(API_ENDPOINTS.links.item(42)).toBe("/api/v1/links/42");
    expect(API_ENDPOINTS.links.archive(42)).toBe("/api/v1/links/42/archive");
    expect(API_ENDPOINTS.links.importCsv(2001)).toBe(
      "/api/v1/applications/2001/links/import",
    );

    expect(API_ENDPOINTS.stats.overview()).toBe("/api/v1/stats/overview");
    expect(API_ENDPOINTS.stats.overview(2001)).toBe(
      "/api/v1/applications/2001/stats/overview",
    );
    expect(API_ENDPOINTS.stats.linkDaily(42)).toBe("/api/v1/stats/links/42/daily");
  });
});
