<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const currentName = computed(() => String(route.name));
const navItems = computed(() => router.getRoutes()
  .filter((item) => {
    const meta = item.meta;
    return meta.navLabel
      && item.name !== route.name
      && (!meta.navFrom || meta.navFrom.includes(currentName.value))
      && !meta.navExclude?.includes(currentName.value)
      && (!meta.requiresTenantAdmin || auth.isTenantAdmin)
      && (!meta.requiresAdmin || auth.isAdmin);
  })
  .sort((left, right) => (left.meta.navOrder ?? 0) - (right.meta.navOrder ?? 0)));

async function logout() {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>{{ route.meta.title }}</h1>
        <p class="sub">当前用户：{{ auth.email }}</p>
      </div>
      <div class="actions">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="btn"
          :class="item.meta.primaryFrom?.includes(currentName) ? '' : 'secondary'"
          :to="item.path"
        >
          {{ item.meta.navLabel }}
        </RouterLink>
        <button class="btn secondary" type="button" @click="logout">退出</button>
      </div>
    </header>

    <slot />
  </div>
</template>

<style scoped>
.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.sub {
  margin: 4px 0 0;
}
</style>
