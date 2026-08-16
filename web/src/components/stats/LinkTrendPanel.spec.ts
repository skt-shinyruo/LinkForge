import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, type App } from "vue";
import LinkTrendPanel from "./LinkTrendPanel.vue";
import componentSource from "./LinkTrendPanel.vue?raw";

describe("LinkTrendPanel", () => {
  let app: App<Element> | null = null;

  const baseProps = {
    linkChartLabels: [],
    linkChartSeries: [],
    linkOptionsError: null,
    linkOptionsHasMore: true,
    linkSearch: "",
    links: [],
    linkStats: [],
    selectedLink: null,
    selectedLinkId: null,
    showLinkChart: false,
  };

  afterEach(() => {
    app?.unmount();
    app = null;
    document.body.innerHTML = "";
  });

  it("exposes bounded link search and cursor continuation through public events", () => {
    const updateSearch = vi.fn();
    const searchLinks = vi.fn();
    const loadMoreLinks = vi.fn();
    const host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp(LinkTrendPanel, {
      linkChartLabels: [],
      linkChartSeries: [],
      linkOptionsError: null,
      linkOptionsHasMore: true,
      linkOptionsLoading: false,
      linkSearch: "",
      links: [],
      linkStats: [],
      onLoadMoreLinks: loadMoreLinks,
      onSearchLinks: searchLinks,
      onUpdateLinkSearch: updateSearch,
      selectedLink: null,
      selectedLinkId: null,
      showLinkChart: false,
    });
    app.mount(host);

    const input = host.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = "campaign";
    input.dispatchEvent(new Event("input", { bubbles: true }));
    host.querySelector<HTMLFormElement>("form")!.dispatchEvent(
      new Event("submit", { bubbles: true, cancelable: true }),
    );
    Array.from(host.querySelectorAll("button"))
      .find((button) => button.textContent?.includes("加载更多"))
      ?.click();

    expect(updateSearch).toHaveBeenCalledWith("campaign");
    expect(searchLinks).toHaveBeenCalledOnce();
    expect(loadMoreLinks).toHaveBeenCalledOnce();
    expect(host.textContent).toContain("暂无匹配短链");
  });

  it("keeps search retry available while showing a partial option failure", () => {
    const searchLinks = vi.fn();
    const host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp(LinkTrendPanel, {
      linkChartLabels: [],
      linkChartSeries: [],
      linkOptionsError: "加载短链选项失败",
      linkOptionsHasMore: false,
      linkOptionsLoading: false,
      linkSearch: "campaign",
      links: [],
      linkStats: [],
      onSearchLinks: searchLinks,
      selectedLink: null,
      selectedLinkId: null,
      showLinkChart: false,
    });
    app.mount(host);

    expect(host.textContent).toContain("加载短链选项失败");
    const searchButton = Array.from(host.querySelectorAll("button"))
      .find((button) => button.textContent?.trim() === "搜索")!;
    expect(searchButton.disabled).toBe(false);
    searchButton.click();
    expect(searchLinks).toHaveBeenCalledOnce();
  });

  it.each([
    { loading: false, searchLabel: "搜索", loadMoreLabel: "加载更多" },
    { loading: true, searchLabel: "搜索中...", loadMoreLabel: "加载中..." },
  ])("keeps asynchronous action dimensions stable while loading=$loading", ({ loading, searchLabel, loadMoreLabel }) => {
    const host = document.createElement("div");
    document.body.appendChild(host);
    app = createApp(LinkTrendPanel, {
      ...baseProps,
      linkOptionsLoading: loading,
    });
    app.mount(host);

    const searchButton = host.querySelector<HTMLButtonElement>(".searchButton")!;
    const loadMoreButton = host.querySelector<HTMLButtonElement>(".loadMore")!;

    expect(searchButton.textContent?.trim()).toBe(searchLabel);
    expect(loadMoreButton.textContent?.trim()).toBe(loadMoreLabel);
    expect(searchButton.classList).toContain("stateButton");
    expect(loadMoreButton.classList).toContain("stateButton");
    expect(componentSource).toMatch(/grid-template-columns:\s*minmax\(0, 1fr\) 5rem/);
    expect(componentSource).toMatch(
      /\.stateButton\s*\{[^}]*inline-size:\s*5rem;[^}]*min-inline-size:\s*5rem;[^}]*white-space:\s*nowrap;/s,
    );
    expect(componentSource).toMatch(
      /select\s*\{[^}]*inline-size:\s*100%;[^}]*min-inline-size:\s*0;[^}]*max-inline-size:\s*100%;/s,
    );
  });
});
