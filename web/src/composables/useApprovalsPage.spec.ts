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
    operationType: "ANALYTICS_DETAIL_EXPORT",
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
      .mockResolvedValueOnce([createApproval(1)])
      .mockResolvedValueOnce([createApproval(1, "EXECUTED")]);
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
    listApprovalsMock.mockResolvedValueOnce([createApproval(7)]).mockResolvedValueOnce([]);
    approveRequestMock.mockResolvedValue(createApproval(7, "EXECUTED"));

    const { useApprovalsPage } = await import("./useApprovalsPage");
    const page = useApprovalsPage();

    await page.load();
    page.setDecisionReason(7, " reviewed for export ");
    await page.approve(7);

    expect(approveRequestMock).toHaveBeenCalledWith(7, { reason: "reviewed for export" });
    expect(page.decisionReasons[7]).toBeUndefined();
  });

});
