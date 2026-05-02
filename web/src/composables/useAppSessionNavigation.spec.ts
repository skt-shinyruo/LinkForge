import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

const pushMock = vi.hoisted(() => vi.fn());
const replaceMock = vi.hoisted(() => vi.fn());

vi.mock("vue-router", () => ({
  useRouter: () => ({
    currentRoute: { value: { path: "/links" } },
    push: pushMock,
    replace: replaceMock,
  }),
}));

vi.mock("../services/http", () => ({
  apiFetch: vi.fn(),
  clearToken: vi.fn(),
  getToken: vi.fn(),
  setToken: vi.fn(),
}));

describe("useAppSessionNavigation", () => {
  beforeEach(() => {
    vi.resetModules();
    setActivePinia(createPinia());
    pushMock.mockReset();
    replaceMock.mockReset();
  });

  it("keeps platform admin identity while hiding tenant-admin-only navigation", async () => {
    const [{ useAuthStore }, { useAppSessionNavigation }] = await Promise.all([
      import("../stores/auth"),
      import("./useAppSessionNavigation"),
    ]);
    const auth = useAuthStore();
    auth.roles = ["PLATFORM_ADMIN"];

    const navigation = useAppSessionNavigation("links");

    expect(navigation.isAdmin.value).toBe(true);
    expect(navigation.isTenantAdmin.value).toBe(false);
    expect(navigation.navItems.map((item) => item.path)).not.toEqual(
      expect.arrayContaining(["/overview", "/applications", "/domains", "/api-keys"]),
    );
    expect(navigation.navItems.map((item) => item.path)).toEqual(
      expect.arrayContaining(["/stats", "/approvals", "/audit"]),
    );
  });
});
