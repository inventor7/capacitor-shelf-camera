package com.efficy.shelfcamera.analyzers

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.exp

/**
 * Computes a sharpness score in [0, 1] using Laplacian variance of a luma Mat.
 * Higher = sharper.
 */
class BlurAnalyzer {

    /**
     * @param lumaMat grayscale Mat (CV_8UC1)
     * @return sharpness score in [0..1]; 1 = perfectly sharp
     */
    fun analyze(lumaMat: Mat): Float {
        val lap = Mat()
        Imgproc.Laplacian(lumaMat, lap, CvType.CV_64F)

        val mean   = MatOfDouble()
        val stddev = MatOfDouble()
        Core.meanStdDev(lap, mean, stddev)

        val variance = stddev.toArray()[0] * stddev.toArray()[0]
        lap.release()

        // Logistic mapping: typical range [0..2000] → [0..1]
        // Inflection at 300, scale 150 provides good separation
        return (1.0 / (1.0 + exp(-(variance - 300.0) / 150.0))).toFloat()
    }
}
