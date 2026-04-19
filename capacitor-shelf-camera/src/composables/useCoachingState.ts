import type { CoachingSignals } from 'capacitor-shelf-camera'
import { computed } from 'vue'
import type { Ref } from 'vue'

export type CoachingLevel = 'ok' | 'warn' | 'block'

export interface CoachingState {
  level: CoachingLevel
  copy: string
  icon: 'steady' | 'slow' | 'tilt' | 'light' | 'overlap' | 'hold' | 'nice' | 'cooldown'
  reason?: string
}

/**
 * Maps a rejection reason from the native KeyframeDecider into a
 * user-facing coaching state. The plugin emits strings like:
 *   "blur too low (0.30 < 0.55)"
 *   "motion too high (0.42 > 0.35)"
 *   "tilt too high (12.3° > 15°)"
 *   "waiting for stillness (motion=0.22)"
 *   "dwell (150ms / 300ms)"  |  "dwell starting"
 *   "overlap too low (18% < 25%)"
 *   "cooldown"
 *   "luma out of range (240)"
 */
function fromRejectionReason(raw: string): CoachingState {
  const r = raw.toLowerCase()

  if (r.startsWith('blur'))
    return { level: 'warn', copy: 'Hold steady', icon: 'steady', reason: raw }
  if (r.startsWith('motion too high') || r.startsWith('waiting for stillness'))
    return { level: 'block', copy: 'Slow down', icon: 'slow', reason: raw }
  if (r.startsWith('tilt'))
    return { level: 'warn', copy: 'Level your phone', icon: 'tilt', reason: raw }
  if (r.startsWith('overlap'))
    return { level: 'warn', copy: 'Keep panning', icon: 'overlap', reason: raw }
  if (r.startsWith('luma'))
    return { level: 'warn', copy: 'Need more light', icon: 'light', reason: raw }
  if (r.startsWith('dwell')) return { level: 'ok', copy: 'Hold…', icon: 'hold', reason: raw }
  if (r.startsWith('cooldown'))
    return { level: 'ok', copy: 'Keep going', icon: 'nice', reason: raw }

  return { level: 'warn', copy: 'Adjust', icon: 'steady', reason: raw }
}

export function useCoachingState(signalsRef: Ref<CoachingSignals | null>) {
  return computed<CoachingState>(() => {
    const s = signalsRef.value
    if (!s) return { level: 'ok', copy: 'Point at shelf', icon: 'nice' }

    // Prefer the plugin's authoritative rejection reason when available
    if (s.rejectionReason) return fromRejectionReason(s.rejectionReason)

    // Fallback heuristic (no active panorama session)
    if (s.motionMagnitude > 0.5) return { level: 'block', copy: 'Slow down', icon: 'slow' }
    if (s.motionMagnitude > 0.3) return { level: 'warn', copy: 'Slow down', icon: 'slow' }

    if (s.blurScore < 0.2) return { level: 'warn', copy: 'Hold steady', icon: 'steady' }

    if (s.tiltDeg > 30) return { level: 'block', copy: 'Level phone', icon: 'tilt' }
    if (s.tiltDeg > 20) return { level: 'warn', copy: 'Level phone', icon: 'tilt' }

    if (s.lumaMean < 30) return { level: 'warn', copy: 'Need more light', icon: 'light' }

    return { level: 'ok', copy: 'Nice', icon: 'nice' }
  })
}
