package com.efficy.shelfcamera.keyframe

import kotlin.math.abs

/**
 * Infers the current grid cell (row, col) from the cumulative sequence of
 * pair-wise translation vectors between consecutive accepted keyframes.
 *
 * Advance rule:
 * - col advances when cumulative horizontal tx exceeds 60% of frame width.
 * - row advances when cumulative vertical ty exceeds 60% of frame height.
 * - Direction reverses allow serpentine (boustrophedon) sweeps.
 */
class GridInferrer(
    private val frameWidthPx: Float  = 1920f,
    private val frameHeightPx: Float = 1080f,
) {
    private val advanceThreshold = 0.60f

    var currentRow = 0
        private set
    var currentCol = 0
        private set

    private var cumulativeTx = 0f
    private var cumulativeTy = 0f
    private var sweepDirection = 1   // +1 = left-to-right, -1 = right-to-left

    /** Feed translation from RANSAC homography — tx/ty in pixels. */
    fun update(tx: Float, ty: Float) {
        cumulativeTx += tx * sweepDirection
        cumulativeTy += ty

        if (cumulativeTx > frameWidthPx * advanceThreshold) {
            currentCol += sweepDirection
            cumulativeTx = 0f
        } else if (cumulativeTx < -frameWidthPx * advanceThreshold) {
            // Reversed sweep mid-row (shouldn't happen in normal flow, but guard it)
            currentCol += sweepDirection
            cumulativeTx = 0f
        }

        if (abs(cumulativeTy) > frameHeightPx * advanceThreshold) {
            currentRow++
            sweepDirection = -sweepDirection   // flip for next row
            currentCol = if (sweepDirection == 1) 0 else currentCol
            cumulativeTy = 0f
            cumulativeTx = 0f
        }
    }

    fun reset() {
        currentRow       = 0
        currentCol       = 0
        cumulativeTx     = 0f
        cumulativeTy     = 0f
        sweepDirection   = 1
    }

    val cell get() = currentRow to currentCol
}
