import { afterEach, describe, expect, it, vi } from "vitest";
import { createApp, h, type App } from "vue";
import { createEmptyEditForm } from "../../composables/links/linkFormCodec";
import type { LinkDto } from "../../services/types";
import LinkListTable from "./LinkListTable.vue";

function createLink(id: number): LinkDto {
  return {
    id,
    tenantId: 13,
    code: `table-${id}`,
    shortUrl: `https://lf.test/r/table-${id}`,
    originalUrl: `https://example.com/table/${id}`,
    enabled: true,
    tags: [],
  };
}

function findButton(container: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(container.querySelectorAll("button")).find((button) =>
    button.textContent?.includes(label),
  ) as HTMLButtonElement | undefined;
}

describe("LinkListTable", () => {
  let app: App<Element> | null = null;
  let host: HTMLDivElement | null = null;

  afterEach(() => {
    app?.unmount();
    host?.remove();
    app = null;
    host = null;
  });

  it("exposes previous/next paging actions through its public props/events", () => {
    const previousPage = vi.fn();
    const nextPage = vi.fn();

    host = document.createElement("div");
    document.body.appendChild(host);

    app = createApp({
      render() {
        return h(LinkListTable as any, {
          items: [createLink(1)],
          loading: false,
          error: null,
          showArchived: false,
          keyword: "",
          editingId: null,
          editForm: createEmptyEditForm(),
          isAdmin: true,
          formatInstantLocal: (value?: string | null) => value ?? "-",
          policySummary: () => "policy",
          statusLabel: () => "启用",
          page: 1,
          size: 25,
          total: 80,
          onPreviousPage: previousPage,
          onNextPage: nextPage,
        });
      },
    });

    app.mount(host);

    const previousButton = findButton(host, "上一页");
    const nextButton = findButton(host, "下一页");

    expect(previousButton).toBeDefined();
    expect(nextButton).toBeDefined();
    expect(previousButton?.disabled).toBe(false);
    expect(nextButton?.disabled).toBe(false);

    previousButton?.click();
    nextButton?.click();

    expect(previousPage).toHaveBeenCalledTimes(1);
    expect(nextPage).toHaveBeenCalledTimes(1);
  });
});
