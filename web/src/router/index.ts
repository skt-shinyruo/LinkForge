import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";

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
    { path: "/login", component: LoginView },
    {
      path: "/overview",
      component: TenantOverviewView,
      meta: { requiresAuth: true, requiresTenantAdmin: true },
    },
    {
      path: "/applications",
      component: ApplicationsView,
      meta: { requiresAuth: true, requiresTenantAdmin: true },
    },
    {
      path: "/applications/:applicationId",
      component: ApplicationDetailView,
      meta: { requiresAuth: true, requiresTenantAdmin: true },
    },
    { path: "/domains", component: DomainsView, meta: { requiresAuth: true, requiresTenantAdmin: true } },
    { path: "/api-keys", component: ApiKeysView, meta: { requiresAuth: true, requiresTenantAdmin: true } },
    { path: "/approvals", component: ApprovalsView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/audit", component: AuditView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/links", component: LinksView, meta: { requiresAuth: true } },
    { path: "/tags", component: TagsView, meta: { requiresAuth: true } },
    { path: "/stats", component: StatsView, meta: { requiresAuth: true } },
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
