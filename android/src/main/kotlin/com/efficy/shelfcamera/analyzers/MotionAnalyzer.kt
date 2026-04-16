package com.efficy.shelfcamera.analyzers

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Measures pan speed between consecutive frames using Farneback dense optical flow.
 * Returns a magnitude in [0, 1] where 1 = maximum tolerable motion.
 */
class MotionAnalyzer {

    private var prevFrame: Mat? = null
    private val targetSize = Size(160.0, 120.0)
    /** Pixel displacement that maps to score 1.0 (capped). */
    private val maxDispPx = 40f

    /**
     * @param lumaMat grayscale Mat (CV_8UC1)
     * @return motion magnitude in [0..1]
     */
    fun analyze(lumaMat: Mat): Float {
        val small = Mat()
        Imgproc.resize(lumaMat, small, targetSize)

        val prev = prevFrame
        if (prev == null) {
            prevFrame = small
            return 0f
        }

        val flow = Mat()
        Video.calcOpticalFlowFarneback(
            prev, small, flow,
            0.5, 3, 15, 3, 5, 1.2, 0,
        )

        val magnitude = computeMedianMagnitude(flow)
        flow.release()
        prev.release()
        prevFrame = small

        return min(magnitude / maxDispPx, 1f)
    }

    private fun computeMedianMagnitude(flow: Mat): Float {
        val magnitudes = mutableListOf<Float>()
        for (r in 0 until flow.rows()) {
            for (c in 0 until flow.cols()) {
                val d = flow.get(r, c)
                magnitudes.add(sqrt((d[0] * d[0] + d[1] * d[1]).toFloat()))
            }
        }
        magnitudes.sort()
        return magnitudes[magnitudes.size / 2]
    }
}
