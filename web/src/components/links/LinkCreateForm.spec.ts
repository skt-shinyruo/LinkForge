import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, h, type App } from "vue";
import { createEmptyCreateForm } from "../../composables/links/linkFormCodec";
import LinkCreateForm from "./LinkCreateForm.vue";

describe("LinkCreateForm", () => {
  let app: App<Element> | null = null;
  let host: HTMLDivElement | null = null;

  afterEach(() => {
    app?.unmount();
    host?.remove();
    app = null;
    host = null;
  });

  it("renders CSV import result counts and row errors for admins", () => {
    host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp({
      render() {
        return h(LinkCreateForm as any, {
          form: createEmptyCreateForm(),
          creating: false,
          importing: false,
          importFileName: "",
          importResult: {
            success: 3,
            failed: 2,
            errors: ["row 4: code duplicated", "row 5: invalid URL"],
          },
          isAdmin: true,
          error: null,
          onCreate: vi.fn(),
          onImport: vi.fn(),
          onExport: vi.fn(),
          onFileChange: vi.fn(),
        });
      },
    });

    app.mount(host);

    expect(host.textContent).toContain("导入结果：成功 3，失败 2");
    expect(host.textContent).toContain("row 4: code duplicated");
    expect(host.textContent).toContain("row 5: invalid URL");
  });
});
