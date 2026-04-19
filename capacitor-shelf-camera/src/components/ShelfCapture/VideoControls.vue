<template>
  <div class="video-controls">
    <div class="countdown" v-if="isRecording">{{ timeLeft.toFixed(1) }}s</div>

    <button
      class="record-btn"
      :class="{ 'is-recording': isRecording, 'is-processing': isProcessing }"
      @click="toggle"
      :disabled="isProcessing"
    >
      <div class="record-inner">
        <!-- Stopped -->
        <div v-if="!isRecording && !isProcessing" class="circle" />
        <!-- Recording -->
        <div v-else-if="isRecording" class="square" />
        <!-- Processing -->
        <LucideLoader2 v-else class="spinner" :size="24" />
      </div>

      <!-- Progress Ring during recording -->
      <svg class="progress-ring" viewBox="0 0 72 72" v-if="isRecording">
        <circle cx="36" cy="36" r="34" class="track" />
        <circle
          cx="36"
          cy="36"
          r="34"
          class="fill"
          :stroke-dasharray="dashArray"
          :stroke-dashoffset="dashOffset"
        />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, computed } from 'vue'
import { LucideLoader2 } from 'lucide-vue-next'

const props = defineProps<{
  isRecording: boolean
  isProcessing: boolean
  maxDurationMs: number
}>()

const emit = defineEmits(['start', 'stop'])

const timeElapsedMs = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const timeLeft = computed(() => {
  return Math.max(0, (props.maxDurationMs - timeElapsedMs.value) / 1000)
})

// SVG ring calc
const radius = 34
const dashArray = 2 * Math.PI * radius
const dashOffset = computed(() => {
  const progress = timeElapsedMs.value / props.maxDurationMs
  return dashArray * (1 - Math.min(1, Math.max(0, progress)))
})

watch(
  () => props.isRecording,
  (recording) => {
    if (recording) {
      timeElapsedMs.value = 0
      const startTime = Date.now()
      timer = setInterval(() => {
        timeElapsedMs.value = Date.now() - startTime
        if (timeElapsedMs.value >= props.maxDurationMs) {
          // Stop automatically
          if (timer) clearInterval(timer)
        }
      }, 50)
    } else {
      if (timer) clearInterval(timer)
    }
  },
)

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function toggle() {
  if (props.isProcessing) return
  if (props.isRecording) {
    emit('stop')
  } else {
    emit('start')
  }
}
</script>

<style scoped>
.video-controls {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.countdown {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 4px 12px;
  border-radius: 99px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  animation: fade-up-in 0.3s var(--ease-out);
}

.record-btn {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform var(--t-micro) var(--ease-spring);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.3);
}

.record-btn:active {
  transform: scale(0.92);
}

.record-btn.is-recording {
  box-shadow: 0 0 0 3px rgba(255, 107, 138, 0.2);
}

.record-btn.is-processing {
  background: rgba(0, 0, 0, 0.6);
  cursor: default;
}

.record-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.circle {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--block); /* Red */
  transition: all var(--t-short) var(--ease-spring);
}

.square {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: var(--block);
  transition: all var(--t-short) var(--ease-spring);
}

.spinner {
  color: var(--prism-1);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.progress-ring {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
  pointer-events: none;
}

.progress-ring .track {
  fill: transparent;
  stroke: transparent;
  stroke-width: 4;
}

.progress-ring .fill {
  fill: transparent;
  stroke: var(--block);
  stroke-width: 4;
  stroke-linecap: round;
  transition: stroke-dashoffset 50ms linear;
}
</style>
