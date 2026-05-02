import { computed } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

export type AppRoutePath =
  | "/overview"
  | "/applications"
  | "/domains"
  | "/api-keys"
  | "/links"
  | "/stats"
  | "/approvals"
  | "/audit"
  | "/tags";

export type AppPageKey =
  | "overview"
  | "applications"
  | "applicationDetail"
  | "domains"
  | "apiKeys"
  | "links"
  | "stats"
  | "approvals"
  | "audit"
  | "tags";

export type AppPageNavItem = {
  label: string;
  path: AppRoutePath;
  variant?: "primary" | "secondary";
};

const tenantAdminOnlyPaths = new Set([
  "/overview",
  "/applications",
  "/domains",
  "/api-keys",
]);

const adminOnlyPaths = new Set([
  "/approvals",
  "/audit",
]);

const pageConfig: Record<
  AppPageKey,
  { title: string; navItems: AppPageNavItem[] }
> = {
  overview: {
    title: "租户概览",
    navItems: [
      { label: "应用", path: "/applications" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
  applications: {
    title: "应用管理",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
  applicationDetail: {
    title: "应用详情",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
    ],
  },
  domains: {
    title: "域名管理",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
  apiKeys: {
    title: "API Key 管理",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
  links: {
    title: "短链管理",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "primary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
      { label: "标签", path: "/tags", variant: "secondary" },
    ],
  },
  stats: {
    title: "统计看板",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
      { label: "标签", path: "/tags", variant: "secondary" },
    ],
  },
  approvals: {
    title: "审批中心",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
  audit: {
    title: "审计日志",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
    ],
  },
  tags: {
    title: "标签管理",
    navItems: [
      { label: "概览", path: "/overview", variant: "secondary" },
      { label: "应用", path: "/applications", variant: "secondary" },
      { label: "域名", path: "/domains", variant: "secondary" },
      { label: "API Keys", path: "/api-keys", variant: "secondary" },
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
      { label: "审批", path: "/approvals", variant: "secondary" },
      { label: "审计", path: "/audit", variant: "secondary" },
    ],
  },
};

export function useAppSessionNavigation(page: AppPageKey) {
  const auth = useAuthStore();
  const router = useRouter();

  const userEmail = computed(() => auth.email);
  const isAdmin = computed(() => auth.isAdmin);
  const isTenantAdmin = computed(() => auth.isTenantAdmin);
  const { title, navItems: rawNavItems } = pageConfig[page];
  const navItems = computed(() => rawNavItems.filter((item) => {
    if (!isTenantAdmin.value && tenantAdminOnlyPaths.has(item.path)) {
      return false;
    }
    if (!isAdmin.value && adminOnlyPaths.has(item.path)) {
      return false;
    }
    return true;
  }));

  async function navigate(path: AppRoutePath) {
    if (router.currentRoute.value.path === path) {
      return;
    }
    await router.push(path);
  }

  async function logout() {
    await auth.logout();
    await router.replace("/login");
  }

  return {
    title,
    navItems: navItems.value,
    userEmail,
    isAdmin,
    isTenantAdmin,
    navigate,
    logout,
  };
}
