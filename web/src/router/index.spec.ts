import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiFetchMock = vi.hoisted(() => vi.fn());
const clearTokenMock = vi.hoisted(() => vi.fn());
const getTokenMock = vi.hoisted(() => vi.fn());
const setTokenMock = vi.hoisted(() => vi.fn());

vi.mock("../services/http", () => ({
  apiFetch: apiFetchMock,
  clearToken: clearTokenMock,
  getToken: getTokenMock,
  setToken: setTokenMock,
}));

describe("router auth bootstrap", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv("VITE_AUTH_MODE", "cookie");
    apiFetchMock.mockReset();
    clearTokenMock.mockReset();
    getTokenMock.mockReset();
    setTokenMock.mockReset();
    window.history.replaceState({}, "", "/");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("redirects protected routes to login when bootstrap cannot authenticate the session", async () => {
    apiFetchMock.mockRejectedValueOnce(new Error("401 Unauthorized"));

    const [{ router }, { useAuthStore }] = await Promise.all([import("./index"), import("../stores/auth")]);
    const auth = useAuthStore();

    await router.push("/links");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/login?redirect=/links");
    expect(auth.initialized).toBe(true);
    expect(auth.email).toBe("");
  });

  it("redirects authenticated admins into the control-plane overview and exposes self-service routes", async () => {
    apiFetchMock.mockResolvedValue({
      code: 0,
      data: {
        email: "admin@example.com",
        tenantId: 21,
        roles: ["TENANT_ADMIN"],
      },
    });

    const [{ router }] = await Promise.all([import("./index"), import("../stores/auth")]);

    await router.push("/login");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/overview");

    const routePaths = router.getRoutes().map((route) => route.path);
    expect(routePaths).toEqual(
      expect.arrayContaining([
        "/overview",
        "/applications",
        "/applications/:applicationId",
        "/domains",
        "/api-keys",
        "/approvals",
        "/audit",
        "/links",
        "/stats",
      ]),
    );
  });

  it("redirects authenticated non-admin users to /links instead of /overview", async () => {
    apiFetchMock.mockResolvedValue({
      code: 0,
      data: {
        email: "user@example.com",
        tenantId: 22,
        roles: ["USER"],
      },
    });

    const [{ router }] = await Promise.all([import("./index"), import("../stores/auth")]);

    await router.push("/login");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/links");
  });

  it("redirects authenticated non-admin users away from admin-only routes", async () => {
    apiFetchMock.mockResolvedValue({
      code: 0,
      data: {
        email: "user@example.com",
        tenantId: 22,
        roles: ["USER"],
      },
    });

    const [{ router }] = await Promise.all([import("./index"), import("../stores/auth")]);

    await router.push("/overview");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/links");
  });

  it("redirects platform admins away from tenant-admin-only routes", async () => {
    apiFetchMock.mockResolvedValue({
      code: 0,
      data: {
        email: "platform@example.com",
        tenantId: 0,
        roles: ["PLATFORM_ADMIN"],
      },
    });

    const [{ router }] = await Promise.all([import("./index"), import("../stores/auth")]);

    await router.push("/overview");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/links");
  });

  it("allows platform admins to use shared governance routes", async () => {
    apiFetchMock.mockResolvedValue({
      code: 0,
      data: {
        email: "platform@example.com",
        tenantId: 0,
        roles: ["PLATFORM_ADMIN"],
      },
    });

    const [{ router }] = await Promise.all([import("./index"), import("../stores/auth")]);

    await router.push("/approvals");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/approvals");

    await router.push("/audit");

    expect(router.currentRoute.value.fullPath).toBe("/audit");
  });
});
