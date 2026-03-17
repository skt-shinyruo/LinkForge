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
  }),
  getters: {
    isAuthed: (s) => (AUTH_MODE === "cookie" ? !!s.email : !!s.token),
    isAdmin: (s) => s.roles.includes("TENANT_ADMIN"),
  },
  actions: {
    async init() {
      if (this.initialized) {
        return;
      }
      this.initialized = true;

      if (AUTH_MODE === "bearer") {
        this.token = getToken();
        if (!this.token) {
          return;
        }
        // bearer 模式：token 已存在时，刷新后需要通过 /me 回填用户信息
        try {
          const r: ApiResponse<any> = await apiFetch<any>("/api/v1/me");
          if (r.code !== 0 || !r.data) {
            return;
          }
          this.email = r.data.email;
          this.tenantId = r.data.tenantId;
          this.roles = Array.isArray(r.data.roles) ? r.data.roles : [];
        } catch {
          // ignore
        }
        return;
      }

      // cookie 模式：通过 /me 取回当前会话信息
      try {
        const r: ApiResponse<any> = await apiFetch<any>("/api/v1/me");
        if (r.code !== 0 || !r.data) {
          return;
        }
        this.email = r.data.email;
        this.tenantId = r.data.tenantId;
        this.roles = Array.isArray(r.data.roles) ? r.data.roles : [];
      } catch {
        // ignore
      }
    },

    async login(email: string, password: string) {
      const r: ApiResponse<AuthResponse> = await apiFetch<AuthResponse>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      if (r.code !== 0 || !r.data?.user) {
        throw new Error(r.message || "登录失败");
      }
      this.email = r.data.user.email;
      this.tenantId = r.data.user.tenantId;
      this.roles = r.data.user.roles;

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
      this.email = "";
      this.tenantId = 0;
      this.roles = [];
      this.token = null;

      if (AUTH_MODE === "cookie") {
        try {
          await apiFetch<void>("/api/v1/auth/logout", { method: "POST" });
        } catch {
          // ignore
        }
      }
      clearToken();
    },

    hydrate() {
      // 路由守卫会在每次跳转调用 hydrate()
      if (AUTH_MODE === "bearer") {
        this.token = getToken();
      }
    },
  },
});
