<template>
  <div class="capture-controls">
    <!-- Cancel (X) -->
    <button class="ctrl-btn ctrl-btn--secondary" @click="handleCancel">
      <LucideX :size="24" />
    </button>

    <!-- Main action button -->
    <button class="ctrl-main" :class="mainClass" @click="handleMain" :disabled="state === 'stitching'">
      <!-- Capture state: sweep ring -->
      <template v-if="state === 'capture'">
        <div class="sweep-ring">
          <div class="sweep-inner">
            <LucideCamera :size="22" />
          </div>
        </div>
      </template>

      <!-- Stitching: spinner -->
      <template v-else-if="state === 'stitching'">
        <div class="stitch-spinner" />
      </template>

      <!-- Reviewing: check -->
      <template v-else>
        <LucideCheck :size="28" class="check-icon" />
      </template>
    </button>

    <!-- Stop / Done -->
    <button class="ctrl-btn ctrl-btn--secondary" @click="handleSecondary">
      <template v-if="state === 'reviewing'">
        <LucideShare2 :size="20" />
      </template>
      <template v-else>
        <LucideSquare :size="18" />
      </template>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { LucideX, LucideCamera, LucideCheck, LucideSquare, LucideShare2 } from 'lucide-vue-next';

const props = defineProps<{
  state: 'capture' | 'stitching' | 'reviewing';
}>();

const emit = defineEmits<{
  cancel: [];
  commit: [];
  stop: [];
}>();

const mainClass = computed(() => `ctrl-main--${props.state}`);

function handleCancel() {
  emit('cancel');
}

function handleMain() {
  if (props.state !== 'stitching') {
    emit('commit');
  }
}

function handleSecondary() {
  emit('stop');
}
</script>

<style scoped>
.capture-controls {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  z-index: 50;
  pointer-events: auto;
  padding-bottom: env(safe-area-inset-bottom, 20px);
  background: linear-gradient(to top, rgba(0, 0, 0, 0.6) 0%, transparent 100%);
}

/* ─── Secondary buttons ─── */
.ctrl-btn {
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    transform var(--t-micro) var(--ease-spring),
    background var(--t-micro);
}
.ctrl-btn:active {
  transform: scale(0.88);
}

.ctrl-btn--secondary {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: var(--text-0);
}
.ctrl-btn--secondary:active {
  background: rgba(255, 255, 255, 0.18);
}

/* ─── Main action button ─── */
.ctrl-main {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    transform var(--t-micro) var(--ease-spring),
    box-shadow var(--t-short);
}
.ctrl-main:active {
  transform: scale(0.92);
}
.ctrl-main:disabled {
  cursor: default;
}

/* Capture: glass ring with camera icon */
.ctrl-main--capture {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
    0 0 0 3px rgba(255, 255, 255, 0.3),
    0 8px 24px rgba(0, 0, 0, 0.3);
}

.sweep-ring {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sweep-inner {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-0);
}

/* Stitching: spinner */
.ctrl-main--stitching {
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(16px);
}

.stitch-spinner {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: var(--prism-5);
  border-right-color: var(--prism-1);
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Reviewing: check button */
.ctrl-main--reviewing {
  background: var(--ok);
  box-shadow: 0 0 24px rgba(126, 240, 198, 0.4);
}

.check-icon {
  color: var(--bg-0);
}
</style>
