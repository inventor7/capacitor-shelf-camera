package com.efficy.shelfcamera

import android.content.Context
import com.efficy.shelfcamera.keyframe.GridInferrer
import com.efficy.shelfcamera.keyframe.KeyframeDecider
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.stitch.StitchEngine
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.util.concurrent.Executors

/**
 * Holds all mutable state for a single panorama capture session.
 * Max one active session at a time; enforced by [ShelfCameraPlugin].
 */
class PanoramaSession(
    val sessionId: String,
    val mode: String,
    thresholdsJson: JSObject?,
    private val plugin: ShelfCameraPlugin,
    private val context: Context,
) {
    val keyframeDecider = KeyframeDecider(
        minBlur       = thresholdsJson?.getDouble("minBlur")?.toFloat()       ?: 0.55f,
        maxMotion     = thresholdsJson?.getDouble("maxMotion")?.toFloat()      ?: 0.35f,
        maxTiltDeg    = thresholdsJson?.getDouble("maxTiltDeg")?.toFloat()     ?: 8f,
        minOverlapPct = thresholdsJson?.getDouble("minOverlapPct")?.toFloat()  ?: 25f,
    )

    val gridInferrer = GridInferrer()
    val stitchEngine = StitchEngine()
    private val keyframeStore = KeyframeStore(context)

    /** Full-res BGR Mats kept in memory for final stitching. */
    val frameMats = mutableListOf<Mat>()

    /** Metadata for each accepted frame (for URI resolution). */
    val acceptedFrames = mutableListOf<KeyframeStore.SavedFrame>()

    var isCancelled = false
        private set

    private val startTimeMs = System.currentTimeMillis()
    private val stitchExecutor = Executors.newSingleThreadExecutor()

    /**
     * Called by [CameraController.captureStill] for repair frames.
     * Replaces the target cell's Mat in [frameMats] or appends.
     */
    fun addRepairFrame(saved: KeyframeStore.SavedFrame, targetCell: JSObject?) {
        val mat = Imgcodecs.imread(saved.fullUri.removePrefix("file://"))
        if (targetCell != null) {
            val row = targetCell.getInt("row")
            val col = targetCell.getInt("col")
            // Find existing frame at that grid cell and replace
            val idx = acceptedFrames.indexOfFirst { it.frameId == "$row:$col" }
            if (idx >= 0 && idx < frameMats.size) {
                frameMats[idx].release()
                frameMats[idx] = mat
                acceptedFrames[idx] = saved
            } else {
                frameMats.add(mat)
                acceptedFrames.add(saved)
            }
        } else {
            frameMats.add(mat)
            acceptedFrames.add(saved)
        }
    }

    /**
     * Commit: runs final full-pass stitch on all accepted frames on a background thread.
     * Resolves the [call] with PanoramaReadyEvent and notifies via [onReady].
     */
    fun commit(call: PluginCall, onReady: (JSObject) -> Unit) {
        stitchExecutor.execute {
            try {
                val result = stitchEngine.stitchAll(frameMats)
                if (!result.success || result.panorama == null) {
                    plugin.bridge.activity.runOnUiThread {
                        call.reject("STITCH_FAILED", "Final stitching failed")
                    }
                    return@execute
                }

                // Save final panorama
                val panoFile = java.io.File(
                    context.filesDir,
                    "shelfcam/sessions/$sessionId/panorama.jpg"
                ).also { it.parentFile?.mkdirs() }
                val params = org.opencv.core.MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92)
                Imgcodecs.imwrite(panoFile.absolutePath, result.panorama, params)

                val event = JSObject().apply {
                    put("sessionId", sessionId)
                    put("uri", "file://${panoFile.absolutePath}")
                    put("width", result.panorama.width())
                    put("height", result.panorama.height())
                    put("gridRows", gridInferrer.currentRow + 1)
                    put("gridCols", gridInferrer.currentCol + 1)
                    put("durationMs", System.currentTimeMillis() - startTimeMs)
                    put("seamScore", result.seamScore.toDouble())
                }

                result.panorama.release()

                plugin.bridge.activity.runOnUiThread {
                    onReady(event)
                    call.resolve(event)
                }
            } catch (e: Exception) {
                plugin.bridge.activity.runOnUiThread {
                    call.reject("STITCH_FAILED", "Commit error: ${e.message}")
                }
            }
        }
    }

    fun cancel() {
        isCancelled = true
        frameMats.forEach { it.release() }
        frameMats.clear()
        acceptedFrames.clear()
        keyframeStore.deleteSession(sessionId)
        stitchExecutor.shutdown()
    }

    fun release() {
        frameMats.forEach { it.release() }
        frameMats.clear()
        stitchExecutor.shutdown()
    }
}
