import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: "jsdom",
    globals: false,
    restoreMocks: true,
    unstubEnvs: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      reportsDirectory: "coverage",
    },
  },
  server: {
    proxy: {
      // 本地开发：将 API 与跳转请求代理到后端
      "/api": "http://localhost:8080",
      "/r": "http://localhost:8080",
    },
  },
});
