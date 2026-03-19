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

describe("router auth bootstrap", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv("VITE_AUTH_MODE", "cookie");
    apiFetchMock.mockReset();
    clearTokenMock.mockReset();
    getTokenMock.mockReset();
    setTokenMock.mockReset();
    window.history.replaceState({}, "", "/");
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("does not redirect an authenticated cookie-mode user to /login after one failed bootstrap request", async () => {
    apiFetchMock
      .mockRejectedValueOnce(new Error("temporary network failure"))
      .mockResolvedValueOnce({
        code: 0,
        data: {
          email: "cookie-user@example.com",
          tenantId: 11,
          roles: ["TENANT_ADMIN"],
        },
      });

    const [{ router }, { useAuthStore }] = await Promise.all([import("./index"), import("../stores/auth")]);
    const auth = useAuthStore();

    await router.push("/links");
    await router.isReady();

    expect(router.currentRoute.value.fullPath).toBe("/links");
    expect(auth.initialized).toBe(false);

    await auth.init();

    expect(apiFetchMock).toHaveBeenCalledTimes(2);
    expect(auth.email).toBe("cookie-user@example.com");
    expect(auth.initialized).toBe(true);
  });
});
