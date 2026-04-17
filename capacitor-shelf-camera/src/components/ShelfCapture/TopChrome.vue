<template>
  <div class="top-chrome">
    <button class="chrome-btn" @click="$emit('back')">
      <LucideChevronLeft :size="24" />
    </button>

    <div class="chrome-center">
      <div class="frame-counter" v-if="frameCount > 0">
        <div class="frame-dot" />
        <span>{{ frameCount }} frames</span>
      </div>
      <span v-else class="chrome-title">Shelf Scan</span>
    </div>

    <button class="chrome-btn" @click="$emit('toggle-diagnostic')">
      <LucideActivity :size="20" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { LucideChevronLeft, LucideActivity } from 'lucide-vue-next';

defineProps<{ frameCount: number }>();
defineEmits(['back', 'toggle-diagnostic']);
</script>

<style scoped>
.top-chrome {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 88px;
  display: flex;
  align-items: flex-end;
  padding: 0 12px 12px;
  z-index: 20;
  background: linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent 100%);
  pointer-events: none;
}

.chrome-btn {
  pointer-events: auto;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: var(--text-0);
  cursor: pointer;
  transition: transform var(--t-micro) var(--ease-spring),
              background var(--t-micro);
  flex-shrink: 0;
}
.chrome-btn:active {
  transform: scale(0.9);
  background: rgba(255,255,255,0.15);
}

.chrome-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chrome-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-0);
  letter-spacing: -0.01em;
  text-shadow: 0 1px 4px rgba(0,0,0,0.5);
}

.frame-counter {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 99px;
  background: rgba(255,255,255,0.06);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.1);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-0);
}

.frame-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ok);
  animation: prism-pulse 1.5s ease-in-out infinite;
}
</style>
