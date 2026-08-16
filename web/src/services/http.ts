import type { ApiResponse } from "./types";
import { decodeApiResponse, type RuntimeValidator } from "./apiContract";

const TOKEN_KEY = "linkforge.token";

type AuthMode = "bearer" | "cookie";
type TokenStorageMode = "local" | "session" | "none";

const AUTH_MODE = (import.meta.env.VITE_AUTH_MODE || "bearer") as AuthMode;
const TOKEN_STORAGE_MODE = (import.meta.env.VITE_TOKEN_STORAGE ||
  (AUTH_MODE === "cookie" ? "none" : "session")) as TokenStorageMode;

const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
const CSRF_ENDPOINT = "/api/v1/auth/csrf";

let onUnauthorized: (() => void) | null = null;
let unauthorizedHandlerInFlight = false;

/** 注册全局 401 回调；microtask 内的重复 401 只触发一次导航。 */
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

/**
 * 执行带认证信息的原始 fetch。
 *
 * bearer 模式只在调用方未设置 Authorization 时补 token；cookie 模式携带 credentials，并在写请求前
 * best-effort 初始化 CSRF cookie。cookie 模式的写请求收到 403 后会让下一次写请求刷新 CSRF cookie，
 * 但不会自动重放已经发出的写操作。响应 401 会清理 token 并通知全局会话处理器，响应仍交给调用方解析。
 */
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

  if (AUTH_MODE === "cookie" && isUnsafeMethod(options.method || "GET") && resp.status === 403) {
    csrfRefreshRequired = true;
  }

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
let csrfRefreshRequired = false;

function expireCsrfCookie() {
  if (typeof document !== "undefined") {
    document.cookie = `${CSRF_COOKIE_NAME}=; Max-Age=0; path=/`;
  }
}

async function ensureCsrfCookie(): Promise<void> {
  if (!csrfInitPromise) {
    csrfInitPromise = (async () => {
      const response = await fetch(CSRF_ENDPOINT, { method: "GET", credentials: "include" });
      if (!response.ok) {
        throw new Error(`CSRF initialization failed (HTTP ${response.status})`);
      }
      if (!getCookie(CSRF_COOKIE_NAME)) {
        throw new Error("CSRF initialization did not set the expected cookie");
      }
    })();
  }
  const pending = csrfInitPromise;
  try {
    await pending;
  } finally {
    if (csrfInitPromise === pending) {
      csrfInitPromise = null;
    }
  }
}

async function attachCsrfHeaderIfNeeded(headers: Headers, method: string): Promise<void> {
  if (!isUnsafeMethod(method)) {
    return;
  }
  if (headers.has(CSRF_HEADER_NAME)) {
    return;
  }

  if (csrfRefreshRequired) {
    expireCsrfCookie();
  }
  let token = csrfRefreshRequired ? null : getCookie(CSRF_COOKIE_NAME);
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
    csrfRefreshRequired = false;
    headers.set(CSRF_HEADER_NAME, token);
  }
}

/**
 * 执行 JSON API 请求并解析统一 `ApiResponse<T>`。
 *
 * 非 2xx 总是抛异常，并把可解析的业务响应保存在 `error.response`；2xx 非 JSON 同样视为协议错误。
 * 下载/上传等非 JSON 流程应直接使用 `authFetch`，避免被该解析器消费 body。
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  validateData?: RuntimeValidator<T>,
  observeResponse?: (response: Response) => void,
): Promise<ApiResponse<T>> {
  const headers = new Headers(options.headers || {});
  if (!(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const resp = await authFetch(path, { ...options, headers });
  observeResponse?.(resp);

  const text = await resp.text();
  let data: ApiResponse<T> | null = null;
  let protocolError: Error | null = null;
  let parseOk = false;
  if (text) {
    try {
      data = decodeApiResponse(JSON.parse(text) as unknown, validateData);
      parseOk = true;
    } catch (caught) {
      data = null;
      protocolError = caught instanceof Error ? caught : new Error("Invalid API response");
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
    throw protocolError ?? new Error(`Invalid JSON response (HTTP ${resp.status})`);
  }

  return data;
}
