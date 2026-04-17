<template>
  <Transition name="tilt-fade">
    <div v-if="visible" class="tilt-indicator">
      <div class="tilt-ring">
        <!-- Fixed reference crosshair -->
        <div class="tilt-crosshair-h" />
        <div class="tilt-crosshair-v" />

        <!-- Rotating horizon -->
        <div
          class="tilt-horizon"
          :class="horizonClass"
          :style="{ transform: `rotate(${normalizedTilt}deg)` }"
        />

        <!-- Tilt degree readout -->
        <div class="tilt-readout" :class="horizonClass">
          {{ Math.abs(normalizedTilt).toFixed(0) }}°
        </div>
      </div>

      <div class="tilt-label">Level your phone</div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  tilt: number;
  showRef: boolean;
}>();

/** Normalize tilt to a ±range for visual rotation. Cap at ±45 for display. */
const normalizedTilt = computed(() => {
    const t = props.tilt;
    // tiltDeg from native is absolute 0-180. Treat 90 as level (landscape) or 0/180 as level (portrait).
    // If the value is near 90, phone is landscape level. Near 0 or 180, phone is portrait.
    // For portrait, ~90 is "level". We show deviation from 90.
    const deviation = t - 90;
    return Math.max(-45, Math.min(45, deviation));
});

const isHighTilt = computed(() => Math.abs(normalizedTilt.value) > 20);
const isWarning = computed(() => Math.abs(normalizedTilt.value) > 8);
const visible = computed(() => props.showRef && isWarning.value);

const horizonClass = computed(() => {
    if (isHighTilt.value) return 'tilt--block';
    return 'tilt--warn';
});
</script>

<style scoped>
.tilt-indicator {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 15;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.tilt-ring {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 1.5px solid rgba(255,255,255,0.15);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tilt-crosshair-h,
.tilt-crosshair-v {
  position: absolute;
  background: rgba(255,255,255,0.12);
}
.tilt-crosshair-h {
  width: 100%;
  height: 1px;
  top: 50%;
}
.tilt-crosshair-v {
  height: 100%;
  width: 1px;
  left: 50%;
}

.tilt-horizon {
  position: absolute;
  width: 90px;
  height: 2.5px;
  border-radius: 2px;
  transition: transform 100ms linear;
  box-shadow: 0 0 8px currentColor;
}
.tilt-horizon.tilt--warn  { background: var(--warn);  color: var(--warn); }
.tilt-horizon.tilt--block { background: var(--block); color: var(--block); }

.tilt-readout {
  position: absolute;
  bottom: -8px;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.tilt-readout.tilt--warn  { color: var(--warn); }
.tilt-readout.tilt--block { color: var(--block); }

.tilt-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-1);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  text-shadow: 0 1px 4px rgba(0,0,0,0.6);
}

.tilt-fade-enter-active,
.tilt-fade-leave-active {
  transition: opacity 300ms var(--ease-out);
}
.tilt-fade-enter-from,
.tilt-fade-leave-to {
  opacity: 0;
}
</style>
