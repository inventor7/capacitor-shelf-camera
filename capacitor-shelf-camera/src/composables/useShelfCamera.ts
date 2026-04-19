import { onUnmounted, ref, shallowRef } from 'vue'
import { ShelfCamera } from 'capacitor-shelf-camera'
import type { CoachingSignals, ShelfCameraError, VideoProgressEvent } from 'capacitor-shelf-camera'
import type { PluginListenerHandle } from '@capacitor/core'
import { useCaptureSettingsStore, type CaptureMode } from '@/stores/captureSettings'

export type CommitResult =
  | { success: true }
  | { success: false; code: ShelfCameraError['code'] | 'UNKNOWN'; message: string }

export function useShelfCamera() {
  const settings = useCaptureSettingsStore()

  const isCameraActive = ref(false)
  const latestFrame = shallowRef<CoachingSignals | null>(null)
  const acceptedKeyframes = ref<string[]>([])
  const seamScore = ref(0)
  const stitchProgress = ref(0)
  const stitchPreviewUri = ref<string | null>(null)
  const panoramaUri = ref<string | null>(null)
  const lastError = shallowRef<ShelfCameraError | null>(null)

  // Video Mode state
  const isRecording = ref(false)
  const videoProgress = ref<VideoProgressEvent | null>(null)

  let listeners: PluginListenerHandle[] = []
  let currentSessionId: string | null = null

  const registerListeners = async () => {
    listeners.push(
      await ShelfCamera.addListener('frame', (signals) => {
        latestFrame.value = signals
      }),
    )
    listeners.push(
      await ShelfCamera.addListener('keyframeAccepted', (e) => {
        acceptedKeyframes.value.push(e.thumbnailUri)
      }),
    )
    listeners.push(
      await ShelfCamera.addListener('stitchProgress', (e) => {
        stitchProgress.value = e.completedCells / Math.max(1, e.totalCells)
        stitchPreviewUri.value = e.previewUri
      }),
    )
    listeners.push(
      await ShelfCamera.addListener('panoramaReady', (e) => {
        panoramaUri.value = e.uri
        seamScore.value = e.seamScore
        isRecording.value = false
        videoProgress.value = null
      }),
    )
    listeners.push(
      await ShelfCamera.addListener('error', (e) => {
        lastError.value = e
        console.error('[ShelfCamera] native error', e)
      }),
    )
    listeners.push(
      await ShelfCamera.addListener('videoProgress', (e) => {
        videoProgress.value = e
      }),
    )
  }

  const startCapture = async (mode: CaptureMode) => {
    try {
      await registerListeners()
      await ShelfCamera.start({ resolution: '1080p', coaching: true })
      isCameraActive.value = true
      currentSessionId = `session-${Date.now()}`
      acceptedKeyframes.value = []
      panoramaUri.value = null
      stitchPreviewUri.value = null
      stitchProgress.value = 0
      lastError.value = null
      isRecording.value = false
      videoProgress.value = null
      hasBegunSession = false

      // We DO NOT start the panorama session here anymore.
      // The session starts when the user explicitly clicks "Start" or "Record" or "Capture".
      // We just emit coaching signals in the meantime!
    } catch (e) {
      console.error('Failed to start capture', e)
    }
  }

  const startAutoSweep = async () => {
    if (!isCameraActive.value) return
    isRecording.value = true // Re-using isRecording visual state to indicate sweep is active
    hasBegunSession = true
    try {
      await ShelfCamera.beginPanorama({
        mode: 'sweep',
        sessionId: currentSessionId!,
        keyframeThresholds: {
          minBlur: settings.thresholds.minBlur,
          maxMotion: settings.thresholds.maxMotion,
          maxTiltDeg: settings.thresholds.maxTiltDeg,
          minOverlapPct: settings.thresholds.minOverlapPct,
        },
      })
    } catch (e: any) {
      isRecording.value = false
      hasBegunSession = false
      lastError.value = { code: 'IO_ERROR', message: e.message || 'Sweep failed' }
    }
  }

  const stop = async () => {
    try {
      await ShelfCamera.removeAllListeners()
      listeners = []
      await ShelfCamera.stop()
      isCameraActive.value = false
      currentSessionId = null
      isRecording.value = false
      hasBegunSession = false
    } catch (e) {
      console.error('Failed to stop camera', e)
    }
  }

  const commit = async (): Promise<CommitResult> => {
    if (!currentSessionId) {
      return { success: false, code: 'ABORTED', message: 'No active session' }
    }
    if (acceptedKeyframes.value.length === 0) {
      return {
        success: false,
        code: 'NO_KEYFRAMES',
        message:
          'No keyframes captured yet. Keep panning the shelf slowly until frames appear below, then try again.',
      }
    }
    try {
      await ShelfCamera.commitPanorama({ sessionId: currentSessionId })
      return { success: true }
    } catch (e: any) {
      const code = (e?.code as ShelfCameraError['code']) || 'UNKNOWN'
      const message = e?.message || String(e)
      lastError.value = { code: code as any, message }
      return { success: false, code, message }
    }
  }

  const cancel = async () => {
    if (!currentSessionId) return
    try {
      await ShelfCamera.cancelPanorama({ sessionId: currentSessionId })
    } catch (e) {
      console.error('Failed to cancel panorama', e)
    }
  }

  // --- Mode-specific Actions ---

  let hasBegunSession = false

  // Manual Mode
  const captureManualFrame = async () => {
    if (!currentSessionId) return
    try {
      if (!hasBegunSession) {
         await ShelfCamera.beginPanorama({
           mode: 'singleShot',
           sessionId: currentSessionId,
         })
         hasBegunSession = true
      }
      const result = await ShelfCamera.capturePhoto({ sessionId: currentSessionId })
      acceptedKeyframes.value.push(result.thumbnailUri)
    } catch (e) {
      console.error('Manual capture failed', e)
    }
  }

  // Video Mode
  const startRecording = async () => {
    if (!currentSessionId) return
    try {
      if (!hasBegunSession) {
         // Create the target session first
         await ShelfCamera.beginPanorama({
           mode: 'video', // "video" mode forces the live feed to bypass Keyframe extraction
           sessionId: currentSessionId,
           keyframeThresholds: {
             minBlur: settings.thresholds.minBlur,
             maxMotion: settings.thresholds.maxMotion,
             maxTiltDeg: settings.thresholds.maxTiltDeg,
             minOverlapPct: settings.thresholds.minOverlapPct,
           },
         })
         hasBegunSession = true
      }
      
      await ShelfCamera.startVideoCapture({
        sessionId: currentSessionId,
        maxDurationMs: settings.thresholds.maxVideoDurationMs, // 10s default
      })
      isRecording.value = true
    } catch (e: any) {
      lastError.value = { code: 'IO_ERROR', message: e.message || 'Recording failed' }
    }
  }

  const stopRecordingAndProcess = async () => {
    if (!currentSessionId || !isRecording.value) return
    try {
      const { videoUri } = await ShelfCamera.stopVideoCapture({ sessionId: currentSessionId })
      isRecording.value = false

      // Trigger extraction which automatically commits at the end
      await ShelfCamera.processVideo({
        sessionId: currentSessionId,
        videoUri,
        keyframeThresholds: {
          minBlur: settings.thresholds.minBlur,
          maxMotion: settings.thresholds.maxMotion,
          maxTiltDeg: settings.thresholds.maxTiltDeg,
          minOverlapPct: settings.thresholds.minOverlapPct,
        },
      })
    } catch (e: any) {
      isRecording.value = false
      lastError.value = { code: 'IO_ERROR', message: e.message || 'Failed to stop video' }
    }
  }

  const stopAutoSweep = async () => {
    if (!currentSessionId || !isRecording.value) return
    isRecording.value = false
    await commit()
  }

  onUnmounted(() => {
    stop()
  })

  return {
    isCameraActive,
    latestFrame,
    acceptedKeyframes,
    stitchProgress,
    stitchPreviewUri,
    panoramaUri,
    seamScore,
    lastError,
    // Methods
    startCapture,
    commit,
    cancel,
    stop,
    // Auto
    startAutoSweep,
    stopAutoSweep,
    // Manual
    captureManualFrame,
    // Video
    isRecording,
    videoProgress,
    startRecording,
    stopRecordingAndProcess,
  }
}
