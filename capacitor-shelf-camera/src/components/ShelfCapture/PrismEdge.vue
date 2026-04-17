<template>
  <div class="prism-edge" :class="edgeClass">
    <div class="prism-edge-inner" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { CoachingState } from '../../composables/useCoachingState';

const props = defineProps<{ state: CoachingState }>();

const edgeClass = computed(() => `prism-edge--${props.state.level}`);
</script>

<style scoped>
.prism-edge {
  pointer-events: none;
  position: fixed;
  inset: 0;
  z-index: 10;
  padding: 2px;
  border-radius: 0;
  transition: all var(--t-short) var(--ease-out);
}

.prism-edge-inner {
  width: 100%;
  height: 100%;
  border-radius: 0;
}

/* OK: conic prism sweep */
.prism-edge--ok {
  background: var(--prism-conic);
  background-size: 100% 100%;
  animation: prism-sweep 4s linear infinite;
  opacity: 0.5;
}
.prism-edge--ok .prism-edge-inner {
  background: transparent;
}

/* Warn: amber solid */
.prism-edge--warn {
  background: none;
  box-shadow: inset 0 0 0 3px var(--warn);
  opacity: 0.8;
}

/* Block: red pulse */
.prism-edge--block {
  background: none;
  box-shadow: inset 0 0 0 4px var(--block);
  animation: prism-pulse 1.2s ease-in-out infinite;
}

@keyframes prism-sweep {
  0%   { filter: hue-rotate(0deg); }
  100% { filter: hue-rotate(360deg); }
}
</style>
