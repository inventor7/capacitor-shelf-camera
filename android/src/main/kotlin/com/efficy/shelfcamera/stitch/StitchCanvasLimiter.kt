package com.efficy.shelfcamera.stitch

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal object StitchCanvasLimiter {
    const val maxCanvasAreaPx = 16L * 1_000L * 1_000L

    private const val retryMargin = 0.96
    private const val minScale = 0.1

    fun scaleForArea(areaPx: Long): Double {
        if (areaPx <= 0L || areaPx <= maxCanvasAreaPx) {
            return 1.0
        }

        return (sqrt(maxCanvasAreaPx.toDouble() / areaPx.toDouble()) * retryMargin)
            .coerceIn(minScale, 1.0)
    }

    fun downscale(frames: List<Mat>, scale: Double): List<Mat> {
        val safeScale = scale.coerceIn(minScale, 1.0)

        return frames.map { frame ->
            if (safeScale >= 0.999) {
                frame.clone()
            } else {
                val scaled = Mat()
                val width = max(1, (frame.cols() * safeScale).roundToInt())
                val height = max(1, (frame.rows() * safeScale).roundToInt())
                Imgproc.resize(
                    frame,
                    scaled,
                    Size(width.toDouble(), height.toDouble()),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA,
                )
                scaled
            }
        }
    }

    fun release(frames: List<Mat>) {
        frames.forEach { it.release() }
    }
}
