import { computed } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

export type AppRoutePath = "/links" | "/stats" | "/tags";

export type AppPageKey = "links" | "stats" | "tags";

export type AppPageNavItem = {
  label: string;
  path: AppRoutePath;
  variant?: "primary" | "secondary";
};

const pageConfig: Record<
  AppPageKey,
  { title: string; navItems: AppPageNavItem[] }
> = {
  links: {
    title: "短链管理",
    navItems: [
      { label: "标签", path: "/tags", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "primary" },
    ],
  },
  stats: {
    title: "统计看板",
    navItems: [
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "标签", path: "/tags", variant: "secondary" },
    ],
  },
  tags: {
    title: "标签管理",
    navItems: [
      { label: "短链", path: "/links", variant: "secondary" },
      { label: "统计", path: "/stats", variant: "secondary" },
    ],
  },
};

export function useAppSessionNavigation(page: AppPageKey) {
  const auth = useAuthStore();
  const router = useRouter();

  const userEmail = computed(() => auth.email);
  const isAdmin = computed(() => auth.isAdmin);
  const { title, navItems } = pageConfig[page];

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
    navItems,
    userEmail,
    isAdmin,
    navigate,
    logout,
  };
}
