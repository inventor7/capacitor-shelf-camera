package com.efficy.shelfcamera.keyframe

import android.content.Context
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.UUID

/**
 * Persists accepted keyframes to disk.
 *
 * Layout: `{filesDir}/shelfcam/sessions/{sessionId}/frames/{frameId}/`
 *   - `full.jpg`  — original resolution, JPEG quality 85
 *   - `thumb.jpg` — longest-edge ≤ 256 px
 */
class KeyframeStore(private val context: Context) {

    data class SavedFrame(
        val frameId: String,
        val fullUri: String,
        val thumbnailUri: String,
    )

    fun save(sessionId: String, mat: Mat): SavedFrame {
        val frameId = UUID.randomUUID().toString()
        val dir = frameDir(sessionId, frameId).also { it.mkdirs() }

        val fullFile  = File(dir, "full.jpg")
        val thumbFile = File(dir, "thumb.jpg")

        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 85)
        Imgcodecs.imwrite(fullFile.absolutePath, mat, params)

        val thumb = buildThumbnail(mat, 256)
        Imgcodecs.imwrite(thumbFile.absolutePath, thumb, params)
        thumb.release()

        return SavedFrame(frameId, fullFile.toFileUri(), thumbFile.toFileUri())
    }

    fun deleteSession(sessionId: String) {
        sessionDir(sessionId).deleteRecursively()
    }

    fun cleanupIntermediates(sessionId: String) {
        File(sessionDir(sessionId), "frames").deleteRecursively()
        File(sessionDir(sessionId), "preview.jpg").delete()
    }

    fun sessionDir(sessionId: String): File =
        File(context.filesDir, "shelfcam/sessions/$sessionId")

    private fun buildThumbnail(mat: Mat, maxEdge: Int): Mat {
        val scale = maxEdge.toDouble() / maxOf(mat.width(), mat.height())
        if (scale >= 1.0) return mat.clone()
        val out = Mat()
        Imgproc.resize(mat, out, Size(mat.width() * scale, mat.height() * scale))
        return out
    }

    private fun frameDir(sessionId: String, frameId: String) =
        File(sessionDir(sessionId), "frames/$frameId")

    private fun File.toFileUri() = "file://${absolutePath}"
}
