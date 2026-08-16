import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuditLogDto } from "../services/types";

const listAuditLogsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/audit", () => ({
  listAuditLogs: listAuditLogsMock,
}));

function log(id: number): AuditLogDto {
  return {
    id,
    tenantId: 5,
    actorUserId: 7,
    actorEmail: "admin@example.test",
    actionType: "APPROVE_REQUEST",
    resourceType: "approval_request",
    resourceId: String(id),
    requestId: id,
    createdAt: "2026-08-15T10:00:00",
  };
}

describe("useAuditPage", () => {
  beforeEach(() => {
    vi.resetModules();
    listAuditLogsMock.mockReset();
  });

  it("refreshes then incrementally appends the next cursor page", async () => {
    listAuditLogsMock
      .mockResolvedValueOnce({ items: [log(3)], hasMore: true, nextCursor: "v1.next" })
      .mockResolvedValueOnce({ items: [log(2)], hasMore: false, nextCursor: null });

    const { useAuditPage } = await import("./useAuditPage");
    const page = useAuditPage();

    await page.load();
    await page.loadMore();

    expect(listAuditLogsMock).toHaveBeenNthCalledWith(2, { cursor: "v1.next" });
    expect(page.logs.value.map((item) => item.id)).toEqual([3, 2]);
    expect(page.hasMore.value).toBe(false);
  });
});
