<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const email = ref("");
const password = ref("");
const loading = ref(false);
const error = ref<string | null>(null);

async function onSubmit() {
  error.value = null;
  loading.value = true;
  try {
    await auth.login(email.value, password.value);
    const redirect = (route.query.redirect as string) || "/links";
    await router.replace(redirect);
  } catch (e: any) {
    error.value = e?.message || "登录失败";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page">
    <h1>LinkForge 管理后台</h1>
    <p class="sub">使用邮箱与密码登录。</p>

    <form class="card" @submit.prevent="onSubmit">
      <label>
        邮箱
        <input v-model="email" type="email" autocomplete="email" required />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <button type="submit" :disabled="loading">
        {{ loading ? "登录中..." : "登录" }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<style scoped>
.page {
  max-width: 520px;
  margin: 40px auto;
  padding: 0 16px;
}
.sub {
  margin-bottom: 12px;
}
.card {
  display: grid;
  gap: 12px;
}
label {
  display: grid;
  gap: 6px;
  font-size: 14px;
}
.error {
  margin: 0;
}
</style>
