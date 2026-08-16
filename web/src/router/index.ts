import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";

declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    navLabel?: string;
    navOrder?: number;
    navFrom?: string[];
    navExclude?: string[];
    primaryFrom?: string[];
    requiresAuth?: boolean;
    requiresAdmin?: boolean;
    requiresTenantAdmin?: boolean;
  }
}

const LoginView = () => import("../views/LoginView.vue");
const TenantOverviewView = () => import("../views/TenantOverviewView.vue");
const ApplicationsView = () => import("../views/ApplicationsView.vue");
const ApplicationDetailView = () => import("../views/ApplicationDetailView.vue");
const DomainsView = () => import("../views/DomainsView.vue");
const ApiKeysView = () => import("../views/ApiKeysView.vue");
const ApprovalsView = () => import("../views/ApprovalsView.vue");
const AuditView = () => import("../views/AuditView.vue");
const LinksView = () => import("../views/LinksView.vue");
const TagsView = () => import("../views/TagsView.vue");
const StatsView = () => import("../views/StatsView.vue");

/**
 * 控制台路由与前端可见性边界。
 *
 * `requiresTenantAdmin` 只允许租户管理员，`requiresAdmin` 同时允许租户管理员和平台管理员。
 * 这些守卫用于导航体验，不能替代后端 `@PreAuthorize` 和 tenant scope 校验。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/links" },
    { path: "/login", name: "login", component: LoginView },
    {
      path: "/overview",
      name: "overview",
      component: TenantOverviewView,
      meta: {
        title: "租户概览",
        navLabel: "概览",
        navOrder: 1,
        requiresAuth: true,
        requiresTenantAdmin: true,
      },
    },
    {
      path: "/applications",
      name: "applications",
      component: ApplicationsView,
      meta: {
        title: "应用管理",
        navLabel: "应用",
        navOrder: 2,
        primaryFrom: ["overview", "application-detail"],
        requiresAuth: true,
        requiresTenantAdmin: true,
      },
    },
    {
      path: "/applications/:applicationId",
      name: "application-detail",
      component: ApplicationDetailView,
      meta: { title: "应用详情", requiresAuth: true, requiresTenantAdmin: true },
    },
    {
      path: "/domains",
      name: "domains",
      component: DomainsView,
      meta: { title: "域名管理", navLabel: "域名", navOrder: 3, requiresAuth: true, requiresTenantAdmin: true },
    },
    {
      path: "/api-keys",
      name: "api-keys",
      component: ApiKeysView,
      meta: { title: "API Key 管理", navLabel: "API Keys", navOrder: 4, requiresAuth: true, requiresTenantAdmin: true },
    },
    {
      path: "/links",
      name: "links",
      component: LinksView,
      meta: { title: "短链管理", navLabel: "短链", navOrder: 5, requiresAuth: true },
    },
    {
      path: "/stats",
      name: "stats",
      component: StatsView,
      meta: { title: "统计看板", navLabel: "统计", navOrder: 6, primaryFrom: ["links"], requiresAuth: true },
    },
    {
      path: "/approvals",
      name: "approvals",
      component: ApprovalsView,
      meta: { title: "审批中心", navLabel: "审批", navOrder: 7, navExclude: ["application-detail"], requiresAuth: true, requiresAdmin: true },
    },
    {
      path: "/audit",
      name: "audit",
      component: AuditView,
      meta: { title: "审计日志", navLabel: "审计", navOrder: 8, navExclude: ["application-detail"], requiresAuth: true, requiresAdmin: true },
    },
    {
      path: "/tags",
      name: "tags",
      component: TagsView,
      meta: { title: "标签管理", navLabel: "标签", navOrder: 9, navFrom: ["links", "stats"], requiresAuth: true },
    },
  ],
});

/**
 * 在解析受保护页面前完成一次会话 bootstrap。
 *
 * `auth.init()` 内部复用同一个 in-flight Promise，因此首次打开时多个导航不会并发请求 `/me`。
 * 未认证时保留原始 `fullPath`，登录页可在成功后恢复目标位置。
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.init();
  if (to.meta.requiresAuth && auth.initialized && !auth.isAuthed) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.path === "/login" && auth.isAuthed) {
    return { path: auth.isTenantAdmin ? "/overview" : "/links" };
  }
  if (to.meta.requiresTenantAdmin && auth.initialized && !auth.isTenantAdmin) {
    return { path: "/links" };
  }
  if (to.meta.requiresAdmin && auth.initialized && !auth.isAdmin) {
    return { path: "/links" };
  }
  return true;
});
