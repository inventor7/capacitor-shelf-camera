<template>
  <kProvider theme="ios">
    <div class="app-root" data-ir-v2="true">
      <router-view v-slot="{ Component, route }">
        <Transition :name="(route.meta.transition as string) ?? 'fade'" mode="out-in">
          <component :is="Component" :key="route.path" />
        </Transition>
      </router-view>
    </div>
  </kProvider>
</template>

<script setup lang="ts">
import { kProvider } from 'konsta/vue'
</script>

<style scoped>
.app-root {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-0);
  color: var(--text-0);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--t-medium, 400ms) var(--ease-out, ease);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-left-enter-active,
.slide-left-leave-active {
  transition: all var(--t-medium, 400ms) var(--ease-out, ease);
}
.slide-left-enter-from {
  opacity: 0;
  transform: translateX(32px);
}
.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-32px);
}
</style>
