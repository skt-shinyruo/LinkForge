import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApprovalRequestDto } from "../services/types";

const approveRequestMock = vi.hoisted(() => vi.fn());
const listApprovalsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/approvals", () => ({
  approveRequest: approveRequestMock,
  listApprovals: listApprovalsMock,
}));

function createApproval(id: number, status = "PENDING_APPROVAL"): ApprovalRequestDto {
  return {
    id,
    tenantId: 5,
    operationType: "PUBLIC_LINK_DESTINATION_CHANGE",
    targetApplicationId: 101,
    requestedByUserId: 7,
    requestedByEmail: "owner@example.com",
    status,
    approverUserId: null,
    approverEmail: null,
    decisionReason: null,
  };
}

describe("useApprovalsPage", () => {
  beforeEach(() => {
    vi.resetModules();
    approveRequestMock.mockReset();
    listApprovalsMock.mockReset();
  });

  it("loads approvals and refreshes after approving a request", async () => {
    listApprovalsMock
      .mockResolvedValueOnce({ items: [createApproval(1)], hasMore: false, nextCursor: null })
      .mockResolvedValueOnce({ items: [createApproval(1, "EXECUTED")], hasMore: false, nextCursor: null });
    approveRequestMock.mockResolvedValue(createApproval(1, "EXECUTED"));

    const { useApprovalsPage } = await import("./useApprovalsPage");
    const page = useApprovalsPage();

    await page.load();
    expect(page.approvals.value[0]?.status).toBe("PENDING_APPROVAL");

    await page.approve(1, "approved");

    expect(approveRequestMock).toHaveBeenCalledWith(1, { reason: "approved" });
    expect(page.approvals.value[0]?.status).toBe("EXECUTED");
  });

  it("stores editable approval reasons and passes the selected reason to the API", async () => {
    listApprovalsMock
      .mockResolvedValueOnce({ items: [createApproval(7)], hasMore: false, nextCursor: null })
      .mockResolvedValueOnce({ items: [], hasMore: false, nextCursor: null });
    approveRequestMock.mockResolvedValue(createApproval(7, "EXECUTED"));

    const { useApprovalsPage } = await import("./useApprovalsPage");
    const page = useApprovalsPage();

    await page.load();
    page.setDecisionReason(7, " reviewed destination change ");
    await page.approve(7);

    expect(approveRequestMock).toHaveBeenCalledWith(7, { reason: "reviewed destination change" });
    expect(page.decisionReasons[7]).toBeUndefined();
  });

  it("loads the next cursor page without replacing existing approvals", async () => {
    listApprovalsMock
      .mockResolvedValueOnce({ items: [createApproval(3)], hasMore: true, nextCursor: "v1.next" })
      .mockResolvedValueOnce({ items: [createApproval(2)], hasMore: false, nextCursor: null });

    const { useApprovalsPage } = await import("./useApprovalsPage");
    const page = useApprovalsPage();

    await page.load();
    await page.loadMore();

    expect(listApprovalsMock).toHaveBeenNthCalledWith(2, { cursor: "v1.next" });
    expect(page.approvals.value.map((approval) => approval.id)).toEqual([3, 2]);
    expect(page.hasMore.value).toBe(false);
  });

});
