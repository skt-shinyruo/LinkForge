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

  it("clears auth state and finishes initialization after /me bootstrap failure", async () => {
    getTokenMock.mockReturnValue("persisted-token");
    apiFetchMock.mockRejectedValueOnce(new Error("401 Unauthorized"));

    const { useAuthStore } = await import("./auth");
    const auth = useAuthStore();

    await auth.init();

    expect(auth.initialized).toBe(true);
    expect(auth.token).toBeNull();
    expect(auth.email).toBe("");
    expect(auth.tenantId).toBe(0);
    expect(auth.roles).toEqual([]);
    expect(clearTokenMock).toHaveBeenCalled();
  });

  it("calls logout on the server before clearing bearer auth state", async () => {
    getTokenMock.mockReturnValue("persisted-token");
    apiFetchMock.mockResolvedValue({ code: 0, data: null });

    const { useAuthStore } = await import("./auth");
    const auth = useAuthStore();
    auth.token = "persisted-token";
    auth.email = "admin@example.com";
    auth.tenantId = 9;
    auth.roles = ["TENANT_ADMIN"];
    auth.initialized = true;

    await auth.logout();

    expect(apiFetchMock).toHaveBeenCalledWith("/api/v1/auth/logout", { method: "POST" });
    expect(apiFetchMock.mock.invocationCallOrder[0]).toBeLessThan(clearTokenMock.mock.invocationCallOrder[0]!);
    expect(auth.token).toBeNull();
    expect(auth.email).toBe("");
    expect(auth.roles).toEqual([]);
  });
});
