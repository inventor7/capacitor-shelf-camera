<template>
  <kSheet :opened="opened" @backdropclick="close" class="settings-sheet">
    <kBlockTitle class="sheet-title">Capture Settings</kBlockTitle>

    <kList strong inset>
      <!-- Blur Threshold -->
      <kListItem class="slider-item">
        <template #title>
          <div class="slider-header">
            <span>Min Blur Quality</span>
            <span class="val">{{ settings.thresholds.minBlur.toFixed(2) }}</span>
          </div>
          <p class="slider-desc">Higher = sharper, but harder to accept.</p>
        </template>
        <template #inner>
          <input
            type="range"
            min="0.1"
            max="0.9"
            step="0.05"
            :value="settings.thresholds.minBlur"
            @input="update($event, 'minBlur', true)"
            class="range-input"
          />
        </template>
      </kListItem>

      <!-- Motion Threshold -->
      <kListItem class="slider-item">
        <template #title>
          <div class="slider-header">
            <span>Max Motion Blur</span>
            <span class="val">{{ settings.thresholds.maxMotion.toFixed(2) }}</span>
          </div>
          <p class="slider-desc">Lower = requires a steadier hand.</p>
        </template>
        <template #inner>
          <input
            type="range"
            min="0.1"
            max="0.9"
            step="0.05"
            :value="settings.thresholds.maxMotion"
            @input="update($event, 'maxMotion', true)"
            class="range-input"
          />
        </template>
      </kListItem>

      <!-- Tilt Threshold -->
      <kListItem class="slider-item">
        <template #title>
          <div class="slider-header">
            <span>Max Tilt Angle (°)</span>
            <span class="val">{{ settings.thresholds.maxTiltDeg.toFixed(0) }}°</span>
          </div>
        </template>
        <template #inner>
          <input
            type="range"
            min="5"
            max="45"
            step="1"
            :value="settings.thresholds.maxTiltDeg"
            @input="update($event, 'maxTiltDeg', false)"
            class="range-input"
          />
        </template>
      </kListItem>

      <!-- Overlap Threshold -->
      <kListItem class="slider-item">
        <template #title>
          <div class="slider-header">
            <span>Min Overlap (%)</span>
            <span class="val">{{ settings.thresholds.minOverlapPct.toFixed(0) }}%</span>
          </div>
        </template>
        <template #inner>
          <input
            type="range"
            min="5"
            max="50"
            step="5"
            :value="settings.thresholds.minOverlapPct"
            @input="update($event, 'minOverlapPct', false)"
            class="range-input"
          />
        </template>
      </kListItem>
    </kList>

    <div class="sheet-actions">
      <kButton clear class="reset-btn" @click="reset">Reset to Defaults</kButton>
      <kButton @click="close" class="done-btn">Done</kButton>
    </div>
  </kSheet>
</template>

<script setup lang="ts">
import { kSheet, kBlockTitle, kList, kListItem, kButton } from 'konsta/vue'
import { useCaptureSettingsStore, type ThresholdSettings } from '../../stores/captureSettings'

defineProps<{ opened: boolean }>()
const emit = defineEmits(['close'])

const settings = useCaptureSettingsStore()

function update(evt: Event, key: keyof ThresholdSettings, isFloat: boolean) {
  const v = (evt.target as HTMLInputElement).value
  const num = isFloat ? parseFloat(v) : parseInt(v, 10)
  settings.updateThreshold(key, num)
}

function reset() {
  settings.resetDefaults()
}

function close() {
  emit('close')
}
</script>

<style scoped>
.settings-sheet {
  --k-sheet-bg-color: var(--bg-1);
}

.sheet-title {
  color: var(--text-0) !important;
  font-size: 18px;
  font-weight: 600;
  margin-top: 16px;
  text-align: center;
}

.slider-item {
  padding: 12px 0;
}

.slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
  color: var(--text-0);
  font-weight: 500;
  font-size: 15px;
}

.slider-header .val {
  color: var(--prism-1);
  font-family: 'SF Mono', monospace;
  font-size: 14px;
}

.slider-desc {
  font-size: 12px;
  color: var(--text-2);
  margin: 0 0 12px 0;
}

.range-input {
  width: 100%;
  accent-color: var(--prism-1);
  margin-top: 8px;
}

.sheet-actions {
  display: flex;
  justify-content: space-between;
  padding: 16px 24px 32px;
}

.reset-btn {
  color: var(--text-2) !important;
}

.done-btn {
  background: var(--prism-gradient) !important;
  color: var(--bg-0) !important;
  font-weight: 600 !important;
  padding: 0 32px;
  border-radius: 99px !important;
}
</style>
