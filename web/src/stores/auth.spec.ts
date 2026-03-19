import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

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

describe("useAuthStore", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv("VITE_AUTH_MODE", "bearer");
    apiFetchMock.mockReset();
    clearTokenMock.mockReset();
    getTokenMock.mockReset();
    setTokenMock.mockReset();
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("retries init after a transient /me failure instead of pinning initialized=true forever", async () => {
    getTokenMock.mockReturnValue("persisted-token");
    apiFetchMock
      .mockRejectedValueOnce(new Error("temporary network failure"))
      .mockResolvedValueOnce({
        code: 0,
        data: {
          email: "admin@example.com",
          tenantId: 7,
          roles: ["TENANT_ADMIN"],
        },
      });

    const { useAuthStore } = await import("./auth");
    const auth = useAuthStore();

    await auth.init();

    expect(auth.initialized).toBe(false);
    expect(auth.email).toBe("");

    await auth.init();

    expect(apiFetchMock).toHaveBeenCalledTimes(2);
    expect(auth.initialized).toBe(true);
    expect(auth.email).toBe("admin@example.com");
    expect(auth.tenantId).toBe(7);
    expect(auth.roles).toEqual(["TENANT_ADMIN"]);
  });
});
