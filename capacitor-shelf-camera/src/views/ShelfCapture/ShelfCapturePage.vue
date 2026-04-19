<template>
  <div class="capture-page">
    <!-- Ghost Frame Guide (disabled per user request)
    <GhostGuide
      :visible="isCameraUp && phase === 'capture' && !!lastAcceptedUri"
      :uri="lastAcceptedUri"
    />
    -->

    <!-- PrismEdge: screen border glow -->
    <PrismEdge v-if="isCameraUp && phase === 'capture'" :state="coaching" />

    <!-- Top Chrome -->
    <TopChrome
      v-if="phase !== 'reviewing'"
      :frame-count="camera.acceptedKeyframes.value.length"
      @back="navigateBack"
      @toggle-diagnostic="showDiag = !showDiag"
      @open-settings="showSettings = true"
    />

    <!-- Capture Mode Selector -->
    <ModeSelector
      v-if="
        isCameraUp &&
        phase === 'capture' &&
        camera.acceptedKeyframes.value.length === 0 &&
        !camera.isRecording.value
      "
      v-model="captureMode"
      :disabled="false"
    />

    <!-- Coaching & Tilt - wrapped in stable div to prevent Vue transition crashes -->
    <div v-show="isCameraUp && phase === 'capture' && !camera.isRecording.value">
      <CoachingPill :state="coaching" />

      <TiltIndicator
        v-if="latestFrame"
        :tilt="latestFrame.tiltDeg"
        :show-ref="coaching.icon === 'tilt'"
      />
    </div>

    <!-- Thumbnail Strip -->
    <ThumbnailStrip v-if="phase === 'capture'" :uris="camera.acceptedKeyframes.value" />

    <!-- Diagnostic overlay -->
    <Transition name="diag-slide">
      <div v-if="showDiag && latestFrame" class="diag-panel glass-surface">
        <div class="diag-header">
          <span>Diagnostics</span>
          <button class="diag-close" @click="showDiag = false">
            <LucideX :size="16" />
          </button>
        </div>
        <div class="diag-grid">
          <div class="diag-item">
            <span class="diag-label">Phase</span>
            <span class="diag-value" style="font-size: 9px">{{
              camera.videoProgress.value?.phase || phase
            }}</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">FPS</span>
            <span class="diag-value" v-if="!camera.videoProgress.value">{{
              Math.round(latestFrame.fps)
            }}</span>
            <span class="diag-value" v-else>video</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">Blur</span>
            <span class="diag-value">{{ latestFrame.blurScore.toFixed(2) }}</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">Motion</span>
            <span class="diag-value">{{ latestFrame.motionMagnitude.toFixed(3) }}</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">Tilt</span>
            <span class="diag-value">{{ latestFrame.tiltDeg.toFixed(1) }}°</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">Overlap</span>
            <span class="diag-value">{{ latestFrame.overlapPct.toFixed(0) }}%</span>
          </div>
          <div class="diag-item">
            <span class="diag-label">Luma</span>
            <span class="diag-value">{{ latestFrame.lumaMean.toFixed(0) }}</span>
          </div>
          <div class="diag-item diag-item--wide">
            <span class="diag-label">Frames</span>
            <span class="diag-value diag-value--ok">
              {{
                camera.videoProgress.value
                  ? `${camera.videoProgress.value.acceptedFrames} acc / ${camera.videoProgress.value.extractedFrames} ext`
                  : camera.acceptedKeyframes.value.length
              }}
            </span>
          </div>
          <div v-if="latestFrame.rejectionReason" class="diag-item diag-item--wide">
            <span class="diag-label">Reject</span>
            <span class="diag-value diag-value--warn">{{ latestFrame.rejectionReason }}</span>
          </div>
          <div v-if="camera.lastError.value" class="diag-item diag-item--wide">
            <span class="diag-label">Error</span>
            <span class="diag-value diag-value--block">{{ camera.lastError.value.message }}</span>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Error toast -->
    <Transition name="toast">
      <div v-if="errorToast" class="error-toast">
        <LucideAlertCircle :size="18" />
        <div class="toast-body">
          <span class="toast-title">{{ errorToast.code }}</span>
          <span class="toast-msg">{{ errorToast.message }}</span>
        </div>
      </div>
    </Transition>

    <!-- Review overlay -->
    <Transition name="review-reveal">
      <div v-if="phase === 'reviewing' && panoramaSrc" class="review-overlay">
        <div class="review-chrome">
          <button class="review-back-btn" @click="navigateBack">
            <LucideChevronLeft :size="22" />
            <span>Done</span>
          </button>
        </div>
        <img :src="panoramaSrc" class="review-image" />
        <div class="review-meta glass-surface">
          <LucideImage :size="16" />
          <span>{{ camera.acceptedKeyframes.value.length }} frames stitched</span>
        </div>
      </div>
    </Transition>

    <!-- Settings Sheet -->
    <SettingsSheet :opened="showSettings" @close="showSettings = false" />

    <!-- Capture Controls -->
    <CaptureControls
      :state="camera.videoProgress.value ? 'stitching' : phase"
      :mode="captureMode"
      :is-recording="camera.isRecording.value"
      :is-processing="!!camera.videoProgress.value"
      :max-video-duration-ms="settings.thresholds.maxVideoDurationMs"
      @cancel="navigateBack"
      @commit="handleCommit"
      @stop="handleStop"
      @start-auto="camera.startAutoSweep"
      @stop-auto="camera.stopAutoSweep"
      @capture-manual="camera.captureManualFrame"
      @start-recording="camera.startRecording"
      @stop-recording="camera.stopRecordingAndProcess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCaptureSettingsStore, type CaptureMode } from '../../stores/captureSettings'
import { useShelfCamera, type CommitResult } from '../../composables/useShelfCamera'
import { useCoachingState } from '../../composables/useCoachingState'
import { toWebSrc } from '../../utils/webSrc'

import PrismEdge from '../../components/ShelfCapture/PrismEdge.vue'
import TopChrome from '../../components/ShelfCapture/TopChrome.vue'
import CoachingPill from '../../components/ShelfCapture/CoachingPill.vue'
import ThumbnailStrip from '../../components/ShelfCapture/ThumbnailStrip.vue'
import CaptureControls from '../../components/ShelfCapture/CaptureControls.vue'
import TiltIndicator from '../../components/ShelfCapture/TiltIndicator.vue'
import GhostGuide from '../../components/ShelfCapture/GhostGuide.vue'
import ModeSelector from '../../components/ShelfCapture/ModeSelector.vue'
import SettingsSheet from '../../components/ShelfCapture/SettingsSheet.vue'

import { LucideX, LucideAlertCircle, LucideChevronLeft, LucideImage } from 'lucide-vue-next'

type Phase = 'capture' | 'stitching' | 'reviewing'

const router = useRouter()
const settings = useCaptureSettingsStore()
const camera = useShelfCamera()
const coaching = useCoachingState(camera.latestFrame)

const captureMode = ref<CaptureMode>(settings.activeMode)
const showSettings = ref(false)

const phase = ref<Phase>('capture')
const showDiag = ref(false)
const errorToast = ref<{ code: string; message: string } | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | null = null

const isCameraUp = computed(() => camera.isCameraActive.value)
const latestFrame = computed(() => camera.latestFrame.value)
const panoramaSrc = computed(() => toWebSrc(camera.panoramaUri.value))

const lastAcceptedUri = computed(() => {
  const uris = camera.acceptedKeyframes.value
  return uris.length > 0 ? uris[uris.length - 1] : undefined
})

// ─── Camera-active transparency ───
watch(
  isCameraUp,
  (active) => {
    document.documentElement.classList.toggle('camera-active', active)
    document.body.classList.toggle('camera-active', active)
  },
  { immediate: true },
)

onUnmounted(() => {
  document.documentElement.classList.remove('camera-active')
  document.body.classList.remove('camera-active')
  camera.stop()
})

// Start capture on mount & on mode switch
watch(
  captureMode,
  async (mode, oldMode) => {
    settings.setMode(mode) // Persist
    if (oldMode && isCameraUp.value) {
      await camera.stop()
    }
    camera.startCapture(mode)
  },
  { immediate: true },
)

// ─── Navigation ───
function navigateBack() {
  camera.stop()
  router.replace({ name: 'home' })
}

function handleStop() {
  camera.stop()
  router.replace({ name: 'home' })
}

// ─── Commit flow ───
function showError(err: Extract<CommitResult, { success: false }>) {
  errorToast.value = err
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    errorToast.value = null
  }, 4500)
}

async function handleCommit() {
  if (phase.value === 'capture') {
    if (camera.acceptedKeyframes.value.length === 0) {
      showError({
        success: false,
        code: 'NO_KEYFRAMES',
        message: 'No frames captured yet. Pan slowly across the shelf.',
      })
      return
    }
    phase.value = 'stitching'
    errorToast.value = null
    const result = await camera.commit()
    if (result.success) {
      phase.value = 'reviewing'
    } else {
      phase.value = 'capture'
      showError(result)
    }
  } else if (phase.value === 'reviewing') {
    navigateBack()
  }
}
</script>

<style scoped>
.capture-page {
  position: fixed;
  inset: 0;
  background: transparent;
  overflow: hidden;
  color: white;
}

/* ─── Diagnostic Panel ─── */
.diag-panel {
  position: fixed;
  top: 96px;
  z-index: 60;
  border-radius: 16px;
  margin: 16px;
  padding: 12px;
  width: 90%;
  animation: fade-up-in 0.3s var(--ease-out);
  background-color: rgba(0, 0, 0, 0.395);
}
.diag-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-0);
}
.diag-close {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  padding: 4px;
}
.diag-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 8px;
}
.diag-item {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  font-size: 11px;
  padding: 3px 0;
}
.diag-item--wide {
  grid-column: span 2;
}
.diag-label {
  color: var(--text-2);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-size: 9px;
}
.diag-value {
  color: var(--text-0);
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 500;
  text-align: right;
}
.diag-value--ok {
  color: var(--ok);
}
.diag-value--warn {
  color: var(--warn);
}
.diag-value--block {
  color: var(--block);
}

/* ─── Error Toast ─── */
.error-toast {
  position: fixed;
  top: 96px;
  left: 16px;
  right: 16px;
  z-index: 70;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 107, 138, 0.92);
  backdrop-filter: blur(16px);
  color: white;
  box-shadow: 0 8px 24px rgba(255, 107, 138, 0.3);
}
.toast-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.toast-title {
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.toast-msg {
  font-size: 13px;
  opacity: 0.9;
  line-height: 1.3;
}

/* ─── Review Overlay ─── */
.review-overlay {
  position: fixed;
  inset: 0;
  z-index: 25;
  background: var(--bg-0);
  display: flex;
  flex-direction: column;
}
.review-chrome {
  padding: 48px 12px 12px;
  display: flex;
  align-items: center;
}
.review-back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #ffffff !important;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  padding: 8px;
}
.review-image {
  flex: 1;
  width: 100%;
  object-fit: contain;
}
.review-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  margin: 12px 16px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
}

/* ─── Transitions ─── */
.diag-slide-enter-active,
.diag-slide-leave-active {
  transition: all 300ms var(--ease-out);
}
.diag-slide-enter-from,
.diag-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 300ms var(--ease-out);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}

.review-reveal-enter-active {
  transition: all var(--t-hero) var(--ease-out);
}
.review-reveal-leave-active {
  transition: all var(--t-medium) var(--ease-out);
}
.review-reveal-enter-from {
  opacity: 0;
  transform: scale(0.96);
}
.review-reveal-leave-to {
  opacity: 0;
}
</style>
