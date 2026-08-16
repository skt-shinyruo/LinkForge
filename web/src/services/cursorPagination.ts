export type CursorPageHeaders = {
  hasMore: boolean;
  nextCursor: string | null;
};

/** Decode the shared bounded-list response headers while keeping body data as an array. */
export function readCursorPageHeaders(response: Response): CursorPageHeaders {
  const rawHasMore = response.headers.get("X-Has-More");
  const nextCursor = response.headers.get("X-Next-Cursor");
  if (rawHasMore !== null && rawHasMore !== "true" && rawHasMore !== "false") {
    throw new Error("Invalid cursor pagination headers");
  }
  const hasMore = rawHasMore === "true";
  if (hasMore && !nextCursor) {
    throw new Error("Invalid cursor pagination headers");
  }
  return { hasMore, nextCursor };
}
