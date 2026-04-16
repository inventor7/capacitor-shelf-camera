package com.efficy.shelfcamera.util

import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer

/**
 * Converts a YUV_420_888 ImageProxy to a grayscale OpenCV Mat (luma plane only).
 * This is the fast path — we never need color for blur/motion/overlap analysis.
 */
fun ImageProxy.toYuvMat(): Mat? {
    val yPlane  = planes.getOrNull(0) ?: return null
    val buffer: ByteBuffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride

    val w = width
    val h = height
    val mat = Mat(h, w, CvType.CV_8UC1)
    val row = ByteArray(rowStride)

    for (r in 0 until h) {
        buffer.position(r * rowStride)
        buffer.get(row, 0, rowStride)
        val data = if (pixelStride == 1) {
            row.copyOf(w)
        } else {
            ByteArray(w) { c -> row[c * pixelStride] }
        }
        mat.put(r, 0, data)
    }
    return mat
}
