package com.efficy.shelfcamera.keyframe

import android.util.Log

/**
 * Pure-function state machine that decides when a coaching-signal reading
 * qualifies as an accepted keyframe.
 *
 * Rules:
 * 1. Signals must pass all thresholds (blur, motion, tilt, luma, overlap).
 * 2. Motion must be low (< 0.15) for ≥ 300 ms before accepting (dwell).
 * 3. After accept, enforces a 400 ms cooldown.
 *
 * [lastRejectionReason] exposes WHY the most recent frame was not accepted —
 * useful for live coaching UI and diagnostic logs.
 */
class KeyframeDecider(
    private val minBlur: Float       = 0.15f,
    private val maxMotion: Float     = 0.50f,
    private val maxTiltDeg: Float    = 25f,
    private val minOverlapPct: Float = 10f,
) {
    private val dwellMs    = 200L
    private val cooldownMs = 300L

    private var lastAcceptMs: Long = 0L
    private var dwellStartMs: Long = 0L
    private var isDwelling: Boolean = false
    private var hasFirstKeyframe: Boolean = false

    /** Reason the most recent evaluate() returned false. Null after an accept. */
    var lastRejectionReason: String? = null
        private set

    data class Signals(
        val blurScore: Float,
        val motionMagnitude: Float,
        val tiltDeg: Float,
        val overlapPct: Float,
        val lumaMean: Float,
        val timestampMs: Long,
    )

    data class Thresholds(
        val minBlur: Float = 0.35f,
        val maxMotion: Float = 0.35f,
        val maxTiltDeg: Float = 20f,
        val minOverlapPct: Float = 20f,
    )


    fun evaluate(s: Signals): Boolean {
        val now = s.timestampMs

        // Cooldown window after a recent accept — silently skip
        if (now - lastAcceptMs < cooldownMs) {
            return reject("cooldown")
        }

        // Threshold gates (report the first one that fails, so the UI can coach)
        if (s.blurScore < minBlur) {
            return reject("blur too low (${"%.2f".format(s.blurScore)} < $minBlur)")
        }
        if (s.motionMagnitude > maxMotion) {
            return reject("motion too high (${"%.2f".format(s.motionMagnitude)} > $maxMotion)")
        }
        if (s.tiltDeg > maxTiltDeg) {
            return reject("tilt too high (${"%.1f".format(s.tiltDeg)}° > ${maxTiltDeg}°)")
        }
        if (s.lumaMean !in 30f..230f) {
            return reject("luma out of range (${"%.0f".format(s.lumaMean)})")
        }
        if (hasFirstKeyframe && s.overlapPct < minOverlapPct) {
            return reject("overlap too low (${"%.0f".format(s.overlapPct)}% < ${minOverlapPct}%)")
        }

        // Dwell: require a sustained low-motion period before snapping.
        val lowMotion = s.motionMagnitude < 0.30f
        if (!lowMotion) {
            isDwelling   = false
            dwellStartMs = 0L
            return reject("waiting for stillness (motion=${"%.2f".format(s.motionMagnitude)})")
        }

        if (!isDwelling) {
            isDwelling   = true
            dwellStartMs = now
            return reject("dwell starting")
        }

        if (now - dwellStartMs < dwellMs) {
            return reject("dwell (${now - dwellStartMs}ms / ${dwellMs}ms)")
        }

        // Accept
        lastAcceptMs         = now
        isDwelling           = false
        hasFirstKeyframe     = true
        lastRejectionReason  = null
        Log.i(TAG, "Keyframe ACCEPTED: blur=${"%.2f".format(s.blurScore)} motion=${"%.2f".format(s.motionMagnitude)} tilt=${"%.1f".format(s.tiltDeg)}° overlap=${"%.0f".format(s.overlapPct)}%")
        return true
    }

    fun reset() {
        lastAcceptMs        = 0L
        dwellStartMs        = 0L
        isDwelling          = false
        hasFirstKeyframe    = false
        lastRejectionReason = null
    }

    private fun reject(reason: String): Boolean {
        if (lastRejectionReason != reason) {
            Log.v(TAG, "Keyframe rejected: $reason")
        }
        lastRejectionReason = reason
        return false
    }

    companion object {
        private const val TAG = "KeyframeDecider"
    }
}
