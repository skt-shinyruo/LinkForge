import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, h, type App, ref } from "vue";
import ApiKeysView from "./ApiKeysView.vue";

const disableMock = vi.hoisted(() => vi.fn());
const enableMock = vi.hoisted(() => vi.fn());

vi.mock("../composables/useAppSessionNavigation", () => ({
  useAppSessionNavigation: () => ({
    title: "API Key 管理",
    userEmail: ref("admin@example.com"),
    navItems: [],
    navigate: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock("../components/AppPageShell.vue", () => ({
  default: {
    props: ["title", "userEmail", "navItems"],
    setup(_: unknown, { slots }: { slots: { default?: () => unknown } }) {
      return () => h("main", slots.default?.() as any);
    },
  },
}));

vi.mock("../composables/useApiKeysPage", () => ({
  useApiKeysPage: () => ({
    actingId: ref(null),
    apiKeys: ref([
      { id: 1, applicationId: 10, name: "active-key", status: "active" },
      { id: 2, applicationId: 10, name: "disabled-key", status: "disabled" },
    ]),
    applications: ref([]),
    create: vi.fn(),
    createForm: { applicationId: null, name: "" },
    creating: ref(false),
    disable: disableMock,
    enable: enableMock,
    error: ref(null),
    latestCreated: ref(null),
    rotate: vi.fn(),
    selectedApplicationId: ref(null),
    setSelectedApplicationId: vi.fn(),
  }),
}));

function findRow(container: HTMLElement, text: string): HTMLTableRowElement {
  const row = Array.from(container.querySelectorAll("tbody tr")).find((element) =>
    element.textContent?.includes(text),
  ) as HTMLTableRowElement | undefined;
  expect(row).toBeDefined();
  return row!;
}

describe("ApiKeysView", () => {
  let app: App<Element> | null = null;
  let host: HTMLDivElement | null = null;

  afterEach(() => {
    app?.unmount();
    host?.remove();
    app = null;
    host = null;
    disableMock.mockClear();
    enableMock.mockClear();
  });

  it("shows Disable for active API keys and Enable for disabled API keys", () => {
    host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp(ApiKeysView);

    app.mount(host);

    const activeRow = findRow(host, "active-key");
    const disabledRow = findRow(host, "disabled-key");

    expect(activeRow.textContent).toContain("禁用");
    expect(activeRow.textContent).not.toContain("启用");
    expect(disabledRow.textContent).toContain("启用");
    expect(disabledRow.textContent).not.toContain("禁用");
  });
});
