package com.efficy.shelfcamera

import android.content.Context
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.stitch.ManualFrameHint
import com.efficy.shelfcamera.stitch.StitchEngine
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.util.concurrent.Executors

class PanoramaSession(
    val sessionId: String,
    expectedCells: Int?,
    private val plugin: ShelfCameraPlugin,
    private val context: Context,
) {
    val stitchEngine = StitchEngine()
    val expectedCellCount = expectedCells?.takeIf { it > 0 }
    val keyframeStore = KeyframeStore(context)
    val frameMats = mutableListOf<Mat>()
    val acceptedFrames = mutableListOf<KeyframeStore.SavedFrame>()
    val manualFrameHints = mutableListOf<ManualFrameHint>()

    var isCancelled = false
        private set

    private val startTimeMs = System.currentTimeMillis()
    private val stitchExecutor = Executors.newSingleThreadExecutor()

    fun commit(call: PluginCall, onReady: (JSObject) -> Unit) {
        stitchExecutor.execute {
            try {
                if (frameMats.isEmpty()) {
                    plugin.bridge.activity.runOnUiThread {
                        call.reject(
                            "No frames captured yet. Capture at least one shelf photo before stitching.",
                            "NO_KEYFRAMES",
                        )
                    }
                    return@execute
                }

                val result = stitchEngine.stitchAllManual(
                    frameMats,
                    manualFrameHints.toList(),
                )
                if (!result.success || result.panorama == null) {
                    plugin.bridge.activity.runOnUiThread {
                        call.reject(
                            result.failureReason ?: "Final stitching failed",
                            "STITCH_FAILED",
                        )
                    }
                    return@execute
                }

                val panoFile = java.io.File(
                    context.filesDir,
                    "shelfcam/sessions/$sessionId/panorama.jpg"
                ).also { it.parentFile?.mkdirs() }
                val params = org.opencv.core.MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 92)
                Imgcodecs.imwrite(panoFile.absolutePath, result.panorama, params)

                try {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "ShelfCam_${sessionId}.jpg")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/ShelfCam")
                        }
                    }
                    val mediaUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    mediaUri?.let { destUri ->
                        context.contentResolver.openOutputStream(destUri)?.use { os ->
                            java.io.FileInputStream(panoFile).use { inputStream ->
                                inputStream.copyTo(os)
                            }
                        }
                    }
                } catch (e: Exception) {
                }

                val acceptedFrameCount = acceptedFrames.size.coerceAtLeast(1)
                keyframeStore.cleanupIntermediates(sessionId)

                val event = JSObject().apply {
                    put("sessionId", sessionId)
                    put("uri", "file://${panoFile.absolutePath}")
                    put("width", result.panorama.width())
                    put("height", result.panorama.height())
                    put("gridRows", 1)
                    put("gridCols", acceptedFrameCount)
                    put("durationMs", System.currentTimeMillis() - startTimeMs)
                    put("seamScore", result.seamScore.toDouble())
                    put("stitchMode", result.stitchMode)
                }

                result.panorama.release()
                acceptedFrames.clear()
                manualFrameHints.clear()

                plugin.bridge.activity.runOnUiThread {
                    onReady(event)
                    call.resolve(event)
                }
            } catch (e: Exception) {
                plugin.bridge.activity.runOnUiThread {
                    call.reject("Commit error: ${e.message}", "STITCH_FAILED")
                }
            } finally {
                frameMats.forEach { it.release() }
                frameMats.clear()
                acceptedFrames.clear()
                manualFrameHints.clear()
            }
        }
    }

    fun cancel() {
        isCancelled = true
        frameMats.forEach { it.release() }
        frameMats.clear()
        acceptedFrames.clear()
        manualFrameHints.clear()
        keyframeStore.deleteSession(sessionId)
        stitchExecutor.shutdown()
    }

    fun release() {
        frameMats.forEach { it.release() }
        frameMats.clear()
        acceptedFrames.clear()
        manualFrameHints.clear()
        stitchExecutor.shutdown()
    }
}
