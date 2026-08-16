import { createApp } from "vue";
import "./style.css";
import App from "./App.vue";
import { router } from "./router";
import { setUnauthorizedHandler } from "./services/http";
import { useAuthStore } from "./stores/auth";

const app = createApp(App);
app.use(router);

setUnauthorizedHandler(() => {
  const auth = useAuthStore();
  auth.clearState();
  if (router.currentRoute.value.path !== "/login") {
    void router.replace({ path: "/login", query: { redirect: router.currentRoute.value.fullPath } });
  }
});
app.mount("#app");
