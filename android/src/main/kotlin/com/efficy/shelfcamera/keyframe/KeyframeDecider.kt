package com.efficy.shelfcamera.keyframe

/**
 * Pure-function state machine that decides when a coaching-signal reading
 * qualifies as an accepted keyframe.
 *
 * Rules:
 * 1. Signals must pass all thresholds.
 * 2. Motion must be low (< 0.15) for ≥ 300 ms before accepting (dwell).
 * 3. After accept, enforces a 400 ms cooldown.
 * 4. Overlap with previous keyframe must be ≥ minOverlapPct.
 */
class KeyframeDecider(
    private val minBlur: Float       = 0.55f,
    private val maxMotion: Float     = 0.35f,
    private val maxTiltDeg: Float    = 8f,
    private val minOverlapPct: Float = 25f,
) {
    private val dwellMs    = 300L
    private val cooldownMs = 400L

    private var lastAcceptMs: Long = 0L
    private var dwellStartMs: Long = 0L
    private var isDwelling: Boolean = false
    private var hasFirstKeyframe: Boolean = false

    data class Signals(
        val blurScore: Float,
        val motionMagnitude: Float,
        val tiltDeg: Float,
        val overlapPct: Float,
        val lumaMean: Float,
        val timestampMs: Long,
    )

    fun evaluate(s: Signals): Boolean {
        val now = s.timestampMs

        // Cooldown
        if (now - lastAcceptMs < cooldownMs) return false

        // Threshold checks
        if (s.blurScore       < minBlur)      return false
        if (s.motionMagnitude > maxMotion)     return false
        if (s.tiltDeg         > maxTiltDeg)    return false
        if (s.lumaMean         !in 30f..230f)  return false
        if (hasFirstKeyframe && s.overlapPct < minOverlapPct) return false

        // Dwell logic
        val lowMotion = s.motionMagnitude < 0.15f
        if (!lowMotion) {
            isDwelling   = false
            dwellStartMs = 0L
            return false
        }

        if (!isDwelling) {
            isDwelling   = true
            dwellStartMs = now
            return false
        }

        if (now - dwellStartMs < dwellMs) return false

        // Accept
        lastAcceptMs      = now
        isDwelling        = false
        hasFirstKeyframe  = true
        return true
    }

    fun reset() {
        lastAcceptMs     = 0L
        dwellStartMs     = 0L
        isDwelling       = false
        hasFirstKeyframe = false
    }
}
