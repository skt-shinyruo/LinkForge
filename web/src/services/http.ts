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

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
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
    onUnauthorized?.();
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
    csrfInitPromise = fetch(CSRF_ENDPOINT, { method: "GET", credentials: "include" }).then(() => {});
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
    await ensureCsrfCookie();
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
  const data = text ? (JSON.parse(text) as ApiResponse<T>) : ({} as ApiResponse<T>);

  // 统一把非 2xx 当作异常，但保留服务端返回的业务 code/message 以便展示
  if (!resp.ok) {
    const msg = data?.message || `HTTP ${resp.status}`;
    const err = new Error(msg) as Error & { response?: ApiResponse<T> };
    err.response = data;
    throw err;
  }

  return data;
}
