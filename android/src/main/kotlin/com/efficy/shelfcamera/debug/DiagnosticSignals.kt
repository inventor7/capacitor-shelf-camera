package com.efficy.shelfcamera.debug

import com.getcapacitor.JSObject

/**
 * Aggregates the full set of diagnostic signals for development/debugging.
 * Only activated when `diagnostic: true` is passed to `start()`.
 *
 * Currently a data-only class; may include timing histograms and
 * memory stats in future versions.
 */
data class DiagnosticSignals(
    val blurScore: Float,
    val motionMagnitude: Float,
    val tiltDeg: Float,
    val overlapPct: Float,
    val lumaMean: Float,
    val fps: Float,
    val analyzerLatencyMs: Long,
    val memoryUsedMb: Float,
) {
    fun toJSObject(): JSObject = JSObject().apply {
        put("blurScore",         blurScore.toDouble())
        put("motionMagnitude",   motionMagnitude.toDouble())
        put("tiltDeg",           tiltDeg.toDouble())
        put("overlapPct",        overlapPct.toDouble())
        put("lumaMean",          lumaMean.toDouble())
        put("fps",               fps.toDouble())
        put("analyzerLatencyMs", analyzerLatencyMs)
        put("memoryUsedMb",      memoryUsedMb.toDouble())
    }
}
