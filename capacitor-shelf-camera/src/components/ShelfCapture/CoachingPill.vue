<template>
  <Transition name="pill-swap" mode="out-in">
    <div class="coaching-pill" :class="pillClass" :key="state.copy">
      <component :is="iconComponent" :size="18" class="pill-icon" />
      <span class="pill-text">{{ state.copy }}</span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { CoachingState } from '../../composables/useCoachingState';
import {
  LucideHand,
  LucideGauge,
  LucideSmartphone,
  LucideSun,
  LucideMoveHorizontal,
  LucideTimer,
  LucideSparkles,
} from 'lucide-vue-next';

const props = defineProps<{ state: CoachingState }>();

const iconMap: Record<CoachingState['icon'], any> = {
  steady:   LucideHand,
  slow:     LucideGauge,
  tilt:     LucideSmartphone,
  light:    LucideSun,
  overlap:  LucideMoveHorizontal,
  hold:     LucideTimer,
  nice:     LucideSparkles,
  cooldown: LucideSparkles,
};

const iconComponent = computed(() => iconMap[props.state.icon] ?? LucideSparkles);

const pillClass = computed(() => `pill--${props.state.level}`);
</script>

<style scoped>
.coaching-pill {
  position: fixed;
  top: 22%;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  pointer-events: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 99px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.1);
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  transition: background var(--t-short) var(--ease-out),
              border-color var(--t-short) var(--ease-out);
}

.pill--ok {
  background: rgba(126,240,198,0.12);
  border-color: rgba(126,240,198,0.25);
}
.pill--ok .pill-icon { color: var(--ok); }

.pill--warn {
  background: rgba(255,178,107,0.15);
  border-color: rgba(255,178,107,0.3);
}
.pill--warn .pill-icon { color: var(--warn); }

.pill--block {
  background: rgba(255,107,138,0.18);
  border-color: rgba(255,107,138,0.35);
  animation: prism-pulse 1.2s ease-in-out infinite;
}
.pill--block .pill-icon { color: var(--block); }

.pill-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-0);
  white-space: nowrap;
  text-shadow: 0 1px 3px rgba(0,0,0,0.4);
}

.pill-icon {
  flex-shrink: 0;
}

/* Cross-fade transition matching D2 spec: 160ms */
.pill-swap-enter-active,
.pill-swap-leave-active {
  transition: opacity 160ms var(--ease-out), transform 160ms var(--ease-out);
}
.pill-swap-enter-from {
  opacity: 0;
  transform: translateX(-50%) translateY(6px);
}
.pill-swap-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-6px);
}
</style>
