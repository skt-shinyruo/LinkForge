import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const CSRF_ENDPOINT = "/api/v1/auth/csrf";

function okResponse() {
  return new Response(null, { status: 204 });
}

describe("cookie-mode CSRF initialization", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv("VITE_AUTH_MODE", "cookie");
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
  });

  it("retries initialization after a non-success response", async () => {
    let initAttempts = 0;
    const writeHeaders: Headers[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        if (initAttempts === 1) {
          return new Response(null, { status: 500 });
        }
        document.cookie = "XSRF-TOKEN=retry-token; path=/";
        return okResponse();
      }
      writeHeaders.push(new Headers(init?.headers));
      return okResponse();
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    await authFetch("/second-write", { method: "POST" });

    expect(initAttempts).toBe(2);
    expect(writeHeaders[1]?.get("X-XSRF-TOKEN")).toBe("retry-token");
  });

  it("retries initialization after a network failure", async () => {
    let initAttempts = 0;
    const writeHeaders: Headers[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        if (initAttempts === 1) {
          throw new TypeError("network unavailable");
        }
        document.cookie = "XSRF-TOKEN=network-retry-token; path=/";
        return okResponse();
      }
      writeHeaders.push(new Headers(init?.headers));
      return okResponse();
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    await authFetch("/second-write", { method: "POST" });

    expect(initAttempts).toBe(2);
    expect(writeHeaders[0]?.has("X-XSRF-TOKEN")).toBe(false);
    expect(writeHeaders[1]?.get("X-XSRF-TOKEN")).toBe("network-retry-token");
  });

  it("does not cache repeated initialization failures", async () => {
    let initAttempts = 0;
    const writeHeaders: Headers[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        return new Response(null, { status: 503 });
      }
      writeHeaders.push(new Headers(init?.headers));
      return new Response(null, { status: 403 });
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    await authFetch("/second-write", { method: "POST" });
    await authFetch("/third-write", { method: "POST" });

    expect(initAttempts).toBe(3);
    expect(writeHeaders).toHaveLength(3);
    expect(writeHeaders.every((headers) => !headers.has("X-XSRF-TOKEN"))).toBe(true);
  });

  it("retries initialization when a successful response does not set the cookie", async () => {
    let initAttempts = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        if (initAttempts === 2) {
          document.cookie = "XSRF-TOKEN=second-token; path=/";
        }
      }
      return okResponse();
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    await authFetch("/second-write", { method: "POST" });

    expect(initAttempts).toBe(2);
  });

  it("initializes again after the browser cookie is cleared", async () => {
    let initAttempts = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        document.cookie = `XSRF-TOKEN=token-${initAttempts}; path=/`;
      }
      return okResponse();
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    document.cookie = "XSRF-TOKEN=; Max-Age=0; path=/";
    await authFetch("/second-write", { method: "POST" });

    expect(initAttempts).toBe(2);
  });

  it("refreshes a rejected CSRF cookie before the next write", async () => {
    let initAttempts = 0;
    const writeHeaders: Headers[] = [];
    document.cookie = "XSRF-TOKEN=stale-token; path=/";
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        document.cookie = "XSRF-TOKEN=fresh-token; path=/";
        return okResponse();
      }
      writeHeaders.push(new Headers(init?.headers));
      return writeHeaders.length === 1
        ? new Response(null, { status: 403 })
        : okResponse();
    }));
    const { authFetch } = await import("./http");

    await authFetch("/first-write", { method: "POST" });
    await authFetch("/second-write", { method: "POST" });

    expect(initAttempts).toBe(1);
    expect(writeHeaders[0]?.get("X-XSRF-TOKEN")).toBe("stale-token");
    expect(writeHeaders[1]?.get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("coalesces concurrent initialization attempts", async () => {
    let resolveInitialization!: (value: Response) => void;
    const initialization = new Promise<Response>((resolve) => {
      resolveInitialization = resolve;
    });
    let initAttempts = 0;
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL) => {
      if (input === CSRF_ENDPOINT) {
        initAttempts += 1;
        return initialization;
      }
      return Promise.resolve(okResponse());
    }));
    const { authFetch } = await import("./http");

    const first = authFetch("/first-write", { method: "POST" });
    const second = authFetch("/second-write", { method: "POST" });
    await Promise.resolve();
    expect(initAttempts).toBe(1);

    document.cookie = "XSRF-TOKEN=shared-token; path=/";
    resolveInitialization(okResponse());
    await Promise.all([first, second]);
  });
});

describe("HTTP transport contract", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv("VITE_AUTH_MODE", "bearer");
    vi.stubEnv("VITE_TOKEN_STORAGE", "session");
    localStorage.clear();
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
    sessionStorage.clear();
  });

  it("adds the stored bearer token without replacing a caller authorization header", async () => {
    const observed: Headers[] = [];
    vi.stubGlobal("fetch", vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      observed.push(new Headers(init?.headers));
      return okResponse();
    }));
    const { authFetch, getToken, setToken } = await import("./http");
    setToken("stored-token");

    await authFetch("/implicit");
    await authFetch("/explicit", { headers: { Authorization: "ApiKey caller-token" } });

    expect(getToken()).toBe("stored-token");
    expect(observed[0]?.get("Authorization")).toBe("Bearer stored-token");
    expect(observed[1]?.get("Authorization")).toBe("ApiKey caller-token");
  });

  it("clears both token stores and coalesces duplicate unauthorized notifications", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 401 })));
    const { authFetch, setUnauthorizedHandler } = await import("./http");
    localStorage.setItem("linkforge.token", "legacy-local-token");
    sessionStorage.setItem("linkforge.token", "session-token");
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await Promise.all([authFetch("/first"), authFetch("/second")]);

    expect(handler).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem("linkforge.token")).toBeNull();
    expect(sessionStorage.getItem("linkforge.token")).toBeNull();
    await new Promise<void>((resolve) => queueMicrotask(resolve));
    await authFetch("/third");
    expect(handler).toHaveBeenCalledTimes(2);
    setUnauthorizedHandler(null);
  });

  it("decodes successful JSON and exposes the raw response to the observer", async () => {
    const response = new Response(JSON.stringify({ code: 0, message: "ok", data: { id: 7 } }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
    vi.stubGlobal("fetch", vi.fn(async () => response));
    const { apiFetch } = await import("./http");
    const observer = vi.fn();

    const result = await apiFetch<{ id: number }>(
      "/resource",
      {},
      (value): value is { id: number } =>
        typeof value === "object" && value !== null && "id" in value,
      observer,
    );

    expect(result.data).toEqual({ id: 7 });
    expect(observer).toHaveBeenCalledWith(response);
  });

  it("lets fetch set the multipart boundary for FormData", async () => {
    const fetchMock = vi.fn(async (_path: string, options?: RequestInit) => {
      expect(new Headers(options?.headers).has("Content-Type")).toBe(false);
      return new Response(JSON.stringify({ code: 0, message: "ok" }), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);
    const { apiFetch } = await import("./http");

    await apiFetch("/upload", { method: "POST", body: new FormData() });
  });

  it("preserves business errors and rejects malformed successful responses", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 40301, message: "forbidden" }), {
        status: 403,
        statusText: "Forbidden",
      }))
      .mockResolvedValueOnce(new Response("not-json", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const { apiFetch } = await import("./http");

    await expect(apiFetch("/forbidden")).rejects.toMatchObject({
      message: "forbidden",
      response: { code: 40301, message: "forbidden" },
    });
    await expect(apiFetch("/malformed")).rejects.toThrow(/JSON|API response/);
  });
});
