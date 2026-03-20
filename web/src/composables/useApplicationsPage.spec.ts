import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApplicationDto } from "../services/types";

const createApplicationMock = vi.hoisted(() => vi.fn());
const listApplicationsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/applications", () => ({
  createApplication: createApplicationMock,
  listApplications: listApplicationsMock,
}));

function createApplication(id: number, key = `app-${id}`): ApplicationDto {
  return {
    id,
    tenantId: 7,
    applicationKey: key,
    displayName: `Application ${id}`,
  };
}

describe("useApplicationsPage", () => {
  beforeEach(() => {
    vi.resetModules();
    createApplicationMock.mockReset();
    listApplicationsMock.mockReset();
  });

  it("loads applications and refreshes after creating one", async () => {
    listApplicationsMock
      .mockResolvedValueOnce([createApplication(1)])
      .mockResolvedValueOnce([createApplication(1), createApplication(2)]);
    createApplicationMock.mockResolvedValue(createApplication(2));

    const { useApplicationsPage } = await import("./useApplicationsPage");
    const page = useApplicationsPage();

    await page.load();
    expect(page.applications.value.map((item) => item.id)).toEqual([1]);

    page.createForm.applicationKey = "orders-api";
    page.createForm.displayName = "Orders API";
    await page.create();

    expect(createApplicationMock).toHaveBeenCalledWith({
      applicationKey: "orders-api",
      displayName: "Orders API",
    });
    expect(page.applications.value.map((item) => item.id)).toEqual([1, 2]);
    expect(page.createForm.applicationKey).toBe("");
    expect(page.createForm.displayName).toBe("");
  });
});
