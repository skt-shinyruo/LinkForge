import { reactive } from "vue";
import { apiFetch, clearToken, getToken, setToken } from "../services/http";
import type { ApiResponse, AuthResponse } from "../services/types";
import { isAuthResponse, isAuthUser } from "../services/runtimeContracts";

type AuthMode = "bearer" | "cookie";
const AUTH_MODE = (import.meta.env.VITE_AUTH_MODE || "bearer") as AuthMode;

const auth = reactive({
  token: AUTH_MODE === "bearer" ? getToken() : null as string | null,
  email: "",
  roles: [] as string[],
  initialized: false,
  initInFlight: null as Promise<void> | null,

  get isAuthed() {
    return AUTH_MODE === "cookie" ? !!this.email : !!this.token;
  },

  get isTenantAdmin() {
    return this.roles.includes("TENANT_ADMIN");
  },

  get isAdmin() {
    return this.roles.includes("TENANT_ADMIN") || this.roles.includes("PLATFORM_ADMIN");
  },

  applyUser(data: AuthResponse["user"]) {
    this.email = data.email;
    this.roles = Array.isArray(data.roles) ? data.roles : [];
  },

  clearState() {
    this.email = "";
    this.roles = [];
    this.token = null;
    this.initialized = true;
    clearToken();
  },

  async init() {
    this.hydrate();
    if (this.initialized) {
      return;
    }
    if (this.initInFlight) {
      return this.initInFlight;
    }
    if (AUTH_MODE === "bearer" && !this.token) {
      this.clearState();
      return;
    }

    this.initInFlight = (async () => {
      try {
        const response: ApiResponse<AuthResponse["user"]> = await apiFetch<AuthResponse["user"]>(
          "/api/v1/me",
          {},
          isAuthUser,
        );
        if (response.code !== 0 || !response.data) {
          this.clearState();
          return;
        }
        this.applyUser(response.data);
        this.initialized = true;
      } catch {
        this.clearState();
      } finally {
        this.initInFlight = null;
      }
    })();
    return this.initInFlight;
  },

  async login(email: string, password: string) {
    const response: ApiResponse<AuthResponse> = await apiFetch<AuthResponse>(
      "/api/v1/auth/login",
      {
        method: "POST",
        body: JSON.stringify({ email, password }),
      },
      isAuthResponse,
    );
    if (response.code !== 0 || !response.data?.user) {
      throw new Error(response.message || "登录失败");
    }
    this.applyUser(response.data.user);
    this.initialized = true;

    if (AUTH_MODE === "bearer") {
      const token = response.data.token;
      if (!token) {
        throw new Error(response.message || "登录失败");
      }
      this.token = token;
      setToken(token);
    } else {
      this.token = null;
      clearToken();
    }
  },

  async logout() {
    try {
      await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
    } catch {
      // Best effort: local logout must still complete.
    } finally {
      this.clearState();
    }
  },

  hydrate() {
    if (AUTH_MODE === "bearer") {
      this.token = getToken();
    }
  },
});

export function useAuthStore() {
  return auth;
}
