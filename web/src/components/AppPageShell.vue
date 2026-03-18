<script setup lang="ts">
import type { AppPageNavItem, AppRoutePath } from "../composables/useAppSessionNavigation";

defineProps<{
  title: string;
  userEmail: string;
  navItems: AppPageNavItem[];
}>();

const emit = defineEmits<{
  navigate: [path: AppRoutePath];
  logout: [];
}>();
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>{{ title }}</h1>
        <p class="sub">当前用户：{{ userEmail }}</p>
      </div>
      <div class="actions">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="btn"
          :class="item.variant === 'secondary' ? 'secondary' : ''"
          type="button"
          @click="emit('navigate', item.path)"
        >
          {{ item.label }}
        </button>
        <button class="btn secondary" type="button" @click="emit('logout')">退出</button>
      </div>
    </header>

    <slot />
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
  margin: 24px auto;
  padding: 0 16px;
}

.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.sub {
  color: #666;
  margin: 4px 0 0;
}

.btn {
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: #111;
  color: #fff;
  cursor: pointer;
}

.btn.secondary {
  background: #444;
}
</style>
