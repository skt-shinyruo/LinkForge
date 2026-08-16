import { describe, expect, it } from "vitest";
import { readCursorPageHeaders } from "./cursorPagination";

describe("cursor pagination response headers", () => {
  it("decodes the shared next cursor shape", () => {
    const response = new Response("[]", {
      headers: {
        "X-Has-More": "true",
        "X-Next-Cursor": "v1.opaque",
      },
    });

    expect(readCursorPageHeaders(response)).toEqual({
      hasMore: true,
      nextCursor: "v1.opaque",
    });
  });

  it("supports an older server without pagination headers as a bounded final page", () => {
    expect(readCursorPageHeaders(new Response("[]"))).toEqual({
      hasMore: false,
      nextCursor: null,
    });
  });

  it("rejects inconsistent metadata instead of looping without a cursor", () => {
    const response = new Response("[]", { headers: { "X-Has-More": "true" } });
    expect(() => readCursorPageHeaders(response)).toThrow("pagination headers");
  });
});
