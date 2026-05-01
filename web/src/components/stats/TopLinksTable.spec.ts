import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, type App } from "vue";
import TopLinksTable from "./TopLinksTable.vue";

const mountedApps: App[] = [];

function mountTopLinksTable(onCopyShort = vi.fn()) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const app = createApp(TopLinksTable, {
    topLinks: [
      {
        linkId: 101,
        code: "abc123",
        shortUrl: "https://go.example.test/r/abc123",
        originalUrl: "https://example.com/a",
        pv: 50,
        uv: 40,
      },
    ],
    topSortBy: "pv",
    onCopyShort,
  });
  app.mount(container);
  mountedApps.push(app);
  return { container, onCopyShort };
}

describe("TopLinksTable", () => {
  afterEach(() => {
    for (const app of mountedApps.splice(0)) {
      app.unmount();
    }
    document.body.innerHTML = "";
  });

  it("renders the published short URL as the link target and label", () => {
    const { container } = mountTopLinksTable();

    const shortUrlLink = container.querySelector<HTMLAnchorElement>("tbody td:nth-child(3) a");

    expect(shortUrlLink?.textContent).toBe("https://go.example.test/r/abc123");
    expect(shortUrlLink?.getAttribute("href")).toBe("https://go.example.test/r/abc123");
  });

  it("emits the published short URL when copying", () => {
    const onCopyShort = vi.fn();
    const { container } = mountTopLinksTable(onCopyShort);

    container.querySelector<HTMLButtonElement>("tbody button")?.click();

    expect(onCopyShort).toHaveBeenCalledWith("https://go.example.test/r/abc123");
  });
});
