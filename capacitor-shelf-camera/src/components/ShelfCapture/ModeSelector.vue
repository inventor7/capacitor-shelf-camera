<template>
  <div class="mode-selector-wrapper">
    <kSegmented class="mode-segmented" :strong="true" :rounded="true">
      <kSegmentedButton
        :active="modelValue === 'auto'"
        @click="selectMode('auto')"
        :disabled="disabled"
      >
        <span class="mode-btn-content">
          <LucideWand2 :size="16" />
          Auto
        </span>
      </kSegmentedButton>

      <kSegmentedButton
        :active="modelValue === 'video'"
        @click="selectMode('video')"
        :disabled="disabled"
      >
        <span class="mode-btn-content">
          <LucideVideo :size="16" />
          Video
        </span>
      </kSegmentedButton>

      <kSegmentedButton
        :active="modelValue === 'manual'"
        @click="selectMode('manual')"
        :disabled="disabled"
      >
        <span class="mode-btn-content">
          <LucideHand :size="16" />
          Manual
        </span>
      </kSegmentedButton>
    </kSegmented>
  </div>
</template>

<script setup lang="ts">
import { kSegmented, kSegmentedButton } from 'konsta/vue'
import { LucideWand2, LucideVideo, LucideHand } from 'lucide-vue-next'
import type { CaptureMode } from '../../stores/captureSettings'

defineProps<{
  modelValue: CaptureMode
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [mode: CaptureMode]
}>()

function selectMode(mode: CaptureMode) {
  emit('update:modelValue', mode)
}
</script>

<style scoped>
.mode-selector-wrapper {
  position: absolute;
  top: 104px; /* Just below TopChrome */
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 10;
  pointer-events: auto;
}

.mode-segmented {
  width: 280px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.mode-btn-content {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-0);
}
</style>
