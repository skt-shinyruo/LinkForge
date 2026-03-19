import type { ApiResponse } from "./types";

const TOKEN_KEY = "linkforge.token";

type AuthMode = "bearer" | "cookie";
type TokenStorageMode = "local" | "session" | "none";

const AUTH_MODE = ((import.meta as any).env?.VITE_AUTH_MODE || "bearer") as AuthMode;
const TOKEN_STORAGE_MODE = ((import.meta as any).env?.VITE_TOKEN_STORAGE ||
  (AUTH_MODE === "cookie" ? "none" : "session")) as TokenStorageMode;

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_ENDPOINT = "/api/v1/auth/csrf";

let onUnauthorized: (() => void) | null = null;
let unauthorizedHandlerInFlight = false;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

function notifyUnauthorized() {
  if (!onUnauthorized || unauthorizedHandlerInFlight) {
    return;
  }
  unauthorizedHandlerInFlight = true;
  try {
    onUnauthorized();
  } finally {
    queueMicrotask(() => {
      unauthorizedHandlerInFlight = false;
    });
  }
}

function storage(): Storage | null {
  if (TOKEN_STORAGE_MODE === "local") {
    return localStorage;
  }
  if (TOKEN_STORAGE_MODE === "session") {
    return sessionStorage;
  }
  return null;
}

export function getToken(): string | null {
  const s = storage();
  return s?.getItem(TOKEN_KEY) || null;
}

export function setToken(token: string) {
  const s = storage();
  s?.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  // 为避免模式切换残留，这里同时清理 local/session
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
}

export async function authFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers || {});

  if (AUTH_MODE === "bearer") {
    const token = getToken();
    if (token && !headers.has("Authorization")) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  if (AUTH_MODE === "cookie") {
    await attachCsrfHeaderIfNeeded(headers, options.method || "GET");
  }

  const resp = await fetch(path, {
    ...options,
    headers,
    credentials: AUTH_MODE === "cookie" ? "include" : options.credentials,
  });

  if (resp.status === 401) {
    clearToken();
    notifyUnauthorized();
  }

  return resp;
}

function isUnsafeMethod(method: string): boolean {
  const m = (method || "GET").toUpperCase();
  return m === "POST" || m === "PUT" || m === "DELETE" || m === "PATCH";
}

function getCookie(name: string): string | null {
  if (typeof document === "undefined" || !document.cookie) {
    return null;
  }
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const m = document.cookie.match(new RegExp(`(?:^|;\\s*)${escaped}=([^;]*)`));
  const value = m?.[1];
  return value == null ? null : decodeURIComponent(value);
}

let csrfInitPromise: Promise<void> | null = null;

async function ensureCsrfCookie(): Promise<void> {
  if (!csrfInitPromise) {
    csrfInitPromise = fetch(CSRF_ENDPOINT, { method: "GET", credentials: "include" })
      .then(() => {})
      .catch((err) => {
        // 若首次初始化失败（网络/临时错误），不要把失败永久缓存住；允许后续重试。
        csrfInitPromise = null;
        throw err;
      });
  }
  await csrfInitPromise;
}

async function attachCsrfHeaderIfNeeded(headers: Headers, method: string): Promise<void> {
  if (!isUnsafeMethod(method)) {
    return;
  }
  if (headers.has(CSRF_HEADER_NAME)) {
    return;
  }

  let token = getCookie(CSRF_COOKIE_NAME);
  if (!token) {
    try {
      await ensureCsrfCookie();
    } catch {
      // best-effort：让请求继续走到服务端，由服务端按 CSRF 规则拒绝（或放行）
      return;
    }
    token = getCookie(CSRF_COOKIE_NAME);
  }
  if (token) {
    headers.set(CSRF_HEADER_NAME, token);
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<ApiResponse<T>> {
  const headers = new Headers(options.headers || {});
  headers.set("Content-Type", headers.get("Content-Type") || "application/json");

  const resp = await authFetch(path, { ...options, headers });

  const text = await resp.text();
  let data: ApiResponse<T> | null = null;
  let parseOk = false;
  if (text) {
    try {
      data = JSON.parse(text) as ApiResponse<T>;
      parseOk = true;
    } catch {
      data = null;
    }
  } else {
    data = {} as ApiResponse<T>;
    parseOk = true;
  }

  // 统一把非 2xx 当作异常，但保留服务端返回的业务 code/message 以便展示
  if (!resp.ok) {
    const msg =
      (parseOk ? data?.message : null) ||
      `${resp.status} ${resp.statusText || ""}`.trim() ||
      `HTTP ${resp.status}`;
    const err = new Error(msg) as Error & { response?: ApiResponse<T> };
    err.response =
      (parseOk ? data : null) || ({ code: resp.status * 100, message: msg } as ApiResponse<T>);
    throw err;
  }

  if (!parseOk || data == null) {
    throw new Error(`Invalid JSON response (HTTP ${resp.status})`);
  }

  return data;
}
