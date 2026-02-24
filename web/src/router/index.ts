import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";

const LoginView = () => import("../views/LoginView.vue");
const LinksView = () => import("../views/LinksView.vue");
const TagsView = () => import("../views/TagsView.vue");
const StatsView = () => import("../views/StatsView.vue");

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/links" },
    { path: "/login", component: LoginView },
    { path: "/links", component: LinksView, meta: { requiresAuth: true } },
    { path: "/tags", component: TagsView, meta: { requiresAuth: true } },
    { path: "/stats", component: StatsView, meta: { requiresAuth: true } },
  ],
});

let initPromise: Promise<void> | null = null;

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!initPromise) {
    initPromise = auth.init();
  }
  await initPromise;
  auth.hydrate();
  if (to.meta.requiresAuth && !auth.isAuthed) {
    return { path: "/login", query: { redirect: to.fullPath } };
  }
  if (to.path === "/login" && auth.isAuthed) {
    return { path: "/links" };
  }
  return true;
});
