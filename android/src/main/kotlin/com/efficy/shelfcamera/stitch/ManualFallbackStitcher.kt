package com.efficy.shelfcamera.stitch

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.opencv.core.Mat
import org.opencv.core.Size

class ManualFallbackStitcher {

    fun stitch(frames: List<Mat>, direction: String, hints: List<ManualFrameHint>): StitchResult {
        if (frames.isEmpty()) {
            return StitchResult(false, null, 0f, "No frames were provided for stitching.")
        }
        if (frames.size == 1) {
            return StitchResult(true, frames[0].clone(), 1f, stitchMode = "canvas")
        }

        val positions = ArrayList<Pair<Double, Double>>(frames.size)
        var cursorX = 0.0
        var cursorY = 0.0
        positions.add(cursorX to cursorY)

        for (index in 1 until frames.size) {
            val hint = hints.getOrNull(index) ?: ManualFrameHint()
            val stepX = horizontalStepPx(frames[index], hint.horizontalShiftPct)
            val stepY = verticalStepPx(frames[index], hint.verticalShiftPct)

            cursorX += if (direction == "left") -stepX else stepX
            cursorY += stepY
            positions.add(cursorX to cursorY)
        }

        val minX = positions.indices.minOf { positions[it].first }
        val minY = positions.indices.minOf { positions[it].second }
        val maxX = positions.indices.maxOf { positions[it].first + frames[it].cols() }
        val maxY = positions.indices.maxOf { positions[it].second + frames[it].rows() }
        val canvasW = ceil(maxX - minX).toInt().coerceAtLeast(1)
        val canvasH = ceil(maxY - minY).toInt().coerceAtLeast(1)
        val canvasArea = canvasW.toLong() * canvasH.toLong()

        if (canvasArea > MAX_CANVAS_AREA_PX) {
            return StitchResult(false, null, 0f, "The stitched panorama would be too large to render.")
        }

        val canvas = Mat.zeros(Size(canvasW.toDouble(), canvasH.toDouble()), frames.first().type())
        positions.forEachIndexed { index, (x, y) ->
            blit(
                canvas = canvas,
                frame = frames[index],
                left = (x - minX).roundToInt(),
                top = (y - minY).roundToInt(),
            )
        }

        return StitchResult(
            true,
            canvas,
            computeSeamScore(canvasW, frames),
            stitchMode = "canvas",
        )
    }

    private fun horizontalStepPx(frame: Mat, shiftPct: Float): Double {
        val ratio =
            if (abs(shiftPct) < 1f) {
                DEFAULT_STEP_RATIO
            } else {
                (abs(shiftPct.toDouble()) / 100.0).coerceIn(MIN_STEP_RATIO, MAX_STEP_RATIO)
            }
        return frame.cols() * ratio
    }

    private fun verticalStepPx(frame: Mat, shiftPct: Float): Double {
        val maxOffset = frame.rows() * MAX_VERTICAL_RATIO
        val rawOffset = -(shiftPct.toDouble() / 100.0) * frame.rows()
        return rawOffset.coerceIn(-maxOffset, maxOffset)
    }

    private fun blit(canvas: Mat, frame: Mat, left: Int, top: Int) {
        val destLeft = left.coerceAtLeast(0)
        val destTop = top.coerceAtLeast(0)
        val destRight = (left + frame.cols()).coerceAtMost(canvas.cols())
        val destBottom = (top + frame.rows()).coerceAtMost(canvas.rows())

        if (destRight <= destLeft || destBottom <= destTop) {
            return
        }

        val srcLeft = (destLeft - left).coerceAtLeast(0)
        val srcTop = (destTop - top).coerceAtLeast(0)
        val srcRight = srcLeft + (destRight - destLeft)
        val srcBottom = srcTop + (destBottom - destTop)

        val source = frame.submat(srcTop, srcBottom, srcLeft, srcRight)
        val target = canvas.submat(destTop, destBottom, destLeft, destRight)
        source.copyTo(target)
        source.release()
        target.release()
    }

    private fun computeSeamScore(canvasW: Int, inputs: List<Mat>): Float {
        val totalInputWidth = inputs.sumOf { it.width() }
        if (totalInputWidth <= 0) return 0f
        val ratio = (canvasW.toFloat() / totalInputWidth.toFloat()).coerceIn(0f, 1.2f)
        val distance = kotlin.math.abs(ratio - 0.6f)
        return (1f - distance).coerceIn(0.5f, 1f)
    }

    private companion object {
        private const val DEFAULT_STEP_RATIO = 0.55
        private const val MIN_STEP_RATIO = 0.28
        private const val MAX_STEP_RATIO = 0.82
        private const val MAX_VERTICAL_RATIO = 0.18
        private const val MAX_CANVAS_AREA_PX = 16L * 1_000L * 1_000L
    }
}
