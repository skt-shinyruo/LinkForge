import { defineStore } from "pinia";
import { apiFetch, clearToken, getToken, setToken } from "../services/http";
import type { ApiResponse, AuthResponse } from "../services/types";

type AuthMode = "bearer" | "cookie";
const AUTH_MODE = (import.meta.env.VITE_AUTH_MODE || "bearer") as AuthMode;

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: (AUTH_MODE === "bearer" ? (getToken() as string | null) : null) as string | null,
    email: "" as string,
    tenantId: 0 as number,
    roles: [] as string[],
    initialized: false as boolean,
    initInFlight: null as Promise<void> | null,
  }),
  getters: {
    isAuthed: (s) => (AUTH_MODE === "cookie" ? !!s.email : !!s.token),
    isAdmin: (s) => s.roles.includes("TENANT_ADMIN") || s.roles.includes("PLATFORM_ADMIN"),
  },
  actions: {
    applyUser(data: any) {
      this.email = data.email;
      this.tenantId = data.tenantId;
      this.roles = Array.isArray(data.roles) ? data.roles : [];
    },

    clearState() {
      this.email = "";
      this.tenantId = 0;
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
          const r: ApiResponse<any> = await apiFetch<any>("/api/v1/me");
          if (r.code !== 0 || !r.data) {
            this.clearState();
            return;
          }
          this.applyUser(r.data);
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
      const r: ApiResponse<AuthResponse> = await apiFetch<AuthResponse>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (r.code !== 0 || !r.data?.user) {
        throw new Error(r.message || "登录失败");
      }
      this.applyUser(r.data.user);
      this.initialized = true;

      if (AUTH_MODE === "bearer") {
        const token = r.data.token;
        if (!token) {
          throw new Error(r.message || "登录失败");
        }
        this.token = token;
        setToken(token);
      } else {
        // cookie 模式：不持久化 token（由服务端 Set-Cookie 保持会话）
        this.token = null;
        clearToken();
      }
    },

    async logout() {
      try {
        await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
      } catch {
        // ignore best-effort logout failures; always clear local auth state
      } finally {
        this.clearState();
      }
    },

    hydrate() {
      // 路由守卫会在每次跳转调用 hydrate()
      if (AUTH_MODE === "bearer") {
        this.token = getToken();
      }
    },
  },
});
