import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, type App } from "vue";
import AppPageShell from "./AppPageShell.vue";
import { useAuthStore } from "../stores/auth";

const replaceMock = vi.hoisted(() => vi.fn());

vi.mock("vue-router", () => ({
  RouterLink: { props: ["to"], template: "<a :href='to'><slot /></a>" },
  useRoute: () => ({ name: "links", meta: { title: "短链管理" } }),
  useRouter: () => ({
    replace: replaceMock,
    getRoutes: () => [
      { name: "overview", path: "/overview", meta: { navLabel: "概览", navOrder: 1, requiresTenantAdmin: true } },
      { name: "stats", path: "/stats", meta: { navLabel: "统计", navOrder: 2 } },
      { name: "approvals", path: "/approvals", meta: { navLabel: "审批", navOrder: 3, requiresAdmin: true } },
    ],
  }),
}));

describe("AppPageShell", () => {
  let app: App<Element> | null = null;
  let host: HTMLDivElement | null = null;

  afterEach(() => {
    app?.unmount();
    host?.remove();
    app = null;
    host = null;
  });

  it("hides tenant-admin navigation from platform admins", () => {
    useAuthStore().roles = ["PLATFORM_ADMIN"];
    host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp(AppPageShell);
    app.mount(host);

    expect(host.textContent).not.toContain("概览");
    expect(host.textContent).toContain("统计");
    expect(host.textContent).toContain("审批");
  });
});
