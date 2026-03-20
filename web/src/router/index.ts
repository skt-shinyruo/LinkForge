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

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/links" },
    { path: "/login", component: LoginView },
    {
      path: "/overview",
      component: TenantOverviewView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: "/applications",
      component: ApplicationsView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    {
      path: "/applications/:applicationId",
      component: ApplicationDetailView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
    { path: "/domains", component: DomainsView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/api-keys", component: ApiKeysView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/approvals", component: ApprovalsView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/audit", component: AuditView, meta: { requiresAuth: true, requiresAdmin: true } },
    { path: "/links", component: LinksView, meta: { requiresAuth: true } },
    { path: "/tags", component: TagsView, meta: { requiresAuth: true } },
    { path: "/stats", component: StatsView, meta: { requiresAuth: true } },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.init();
  if (to.meta.requiresAuth && auth.initialized && !auth.isAuthed) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.path === "/login" && auth.isAuthed) {
    return { path: auth.isAdmin ? "/overview" : "/links" };
  }
  if (to.meta.requiresAdmin && auth.initialized && !auth.isAdmin) {
    return { path: "/links" };
  }
  return true;
});
