import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type CaptureMode = 'auto' | 'video' | 'manual'

export interface ThresholdSettings {
  minBlur: number
  maxMotion: number
  maxTiltDeg: number
  minOverlapPct: number
  maxVideoDurationMs: number
}

const DEFAULTS: ThresholdSettings = {
  minBlur: 0.35,
  maxMotion: 0.35,
  maxTiltDeg: 20,
  minOverlapPct: 20,
  maxVideoDurationMs: 10_000,
}

const STORAGE_KEY = 'shelf-cam-settings'

function loadFromStorage(): Partial<ThresholdSettings> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export const useCaptureSettingsStore = defineStore('captureSettings', () => {
  const stored = loadFromStorage()

  const activeMode = ref<CaptureMode>('auto')
  const thresholds = ref<ThresholdSettings>({ ...DEFAULTS, ...stored })

  function setMode(mode: CaptureMode) {
    activeMode.value = mode
  }

  function updateThreshold<K extends keyof ThresholdSettings>(key: K, value: ThresholdSettings[K]) {
    thresholds.value[key] = value
  }

  function resetDefaults() {
    thresholds.value = { ...DEFAULTS }
  }

  // Persist thresholds on change
  watch(
    thresholds,
    (val) => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
      } catch {
        /* quota exceeded, ignore */
      }
    },
    { deep: true },
  )

  return { activeMode, thresholds, setMode, updateThreshold, resetDefaults }
})
