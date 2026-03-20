import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ApplicationDto, DomainDto } from "../services/types";

const authorizeDomainMock = vi.hoisted(() => vi.fn());
const createApplicationDomainMock = vi.hoisted(() => vi.fn());
const createTenantSharedDomainMock = vi.hoisted(() => vi.fn());
const listApplicationsMock = vi.hoisted(() => vi.fn());
const listDomainsMock = vi.hoisted(() => vi.fn());

vi.mock("../services/applications", () => ({
  listApplications: listApplicationsMock,
}));

vi.mock("../services/domains", () => ({
  authorizeDomain: authorizeDomainMock,
  createApplicationDomain: createApplicationDomainMock,
  createTenantSharedDomain: createTenantSharedDomainMock,
  listDomains: listDomainsMock,
}));

function createApplication(id: number): ApplicationDto {
  return {
    id,
    tenantId: 9,
    applicationKey: `app-${id}`,
    displayName: `App ${id}`,
  };
}

function createDomain(id: number, applicationId: number | null, scope: DomainDto["scope"]): DomainDto {
  return {
    id,
    tenantId: 9,
    applicationId,
    hostname: `d-${id}.example.test`,
    scope,
  };
}

describe("useDomainsPage", () => {
  beforeEach(() => {
    vi.resetModules();
    authorizeDomainMock.mockReset();
    createApplicationDomainMock.mockReset();
    createTenantSharedDomainMock.mockReset();
    listApplicationsMock.mockReset();
    listDomainsMock.mockReset();
  });

  it("creates shared domains, dedicated domains, and application authorizations", async () => {
    listApplicationsMock.mockResolvedValue([createApplication(11), createApplication(12)]);
    listDomainsMock
      .mockResolvedValueOnce([createDomain(1, null, "TENANT_SHARED")])
      .mockResolvedValueOnce([createDomain(1, null, "TENANT_SHARED"), createDomain(2, null, "TENANT_SHARED")])
      .mockResolvedValueOnce([
        createDomain(1, null, "TENANT_SHARED"),
        createDomain(2, null, "TENANT_SHARED"),
        createDomain(3, 11, "APPLICATION_DEDICATED"),
      ])
      .mockResolvedValueOnce([
        createDomain(1, null, "TENANT_SHARED"),
        createDomain(2, null, "TENANT_SHARED"),
        createDomain(3, 11, "APPLICATION_DEDICATED"),
      ]);
    createTenantSharedDomainMock.mockResolvedValue(createDomain(2, null, "TENANT_SHARED"));
    createApplicationDomainMock.mockResolvedValue(createDomain(3, 11, "APPLICATION_DEDICATED"));
    authorizeDomainMock.mockResolvedValue(undefined);

    const { useDomainsPage } = await import("./useDomainsPage");
    const page = useDomainsPage();

    await page.load();
    expect(page.domains.value.map((item) => item.id)).toEqual([1]);

    page.createForm.hostname = "shared.example.test";
    page.createForm.applicationId = null;
    await page.create();
    expect(createTenantSharedDomainMock).toHaveBeenCalledWith({ hostname: "shared.example.test" });

    page.createForm.hostname = "app.example.test";
    page.createForm.applicationId = 11;
    await page.create();
    expect(createApplicationDomainMock).toHaveBeenCalledWith(11, { hostname: "app.example.test" });

    page.authorizationForm.applicationId = 12;
    page.authorizationForm.domainId = 1;
    await page.authorize();
    expect(authorizeDomainMock).toHaveBeenCalledWith(12, 1);
  });
});
