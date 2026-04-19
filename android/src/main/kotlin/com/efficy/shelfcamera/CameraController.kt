package com.efficy.shelfcamera

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.ScaleType
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.efficy.shelfcamera.analyzers.FrameAnalyzer
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.sensors.TiltSensor
import com.efficy.shelfcamera.util.EventEmitter
import com.getcapacitor.Bridge
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import java.util.concurrent.Executors
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Owns the CameraX lifecycle: binds Preview + ImageAnalysis + ImageCapture use-cases and routes
 * frames to [FrameAnalyzer] for per-frame coaching signals.
 */
class CameraController(
        private val context: Context,
        private val activity: FragmentActivity,
        private val emitter: EventEmitter,
        private val tiltSensor: TiltSensor,
        private val bridge: Bridge,
) {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val stitchExecutor = Executors.newSingleThreadExecutor()
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var frameAnalyzer: FrameAnalyzer? = null
    private val keyframeStore = KeyframeStore(context)

    fun start(resolution: Size, call: PluginCall) {
        activity.runOnUiThread {
            bridge.webView.setBackgroundColor(Color.TRANSPARENT)
            val parent = bridge.webView.parent as? ViewGroup

            previewView =
                    PreviewView(context).apply {
                        layoutParams =
                                ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                )
                        scaleType = ScaleType.FIT_CENTER
                    }
            parent?.addView(previewView, 0) // add underneath WebView

            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                    {
                        try {
                            provider = future.get()

                            previewUseCase =
                                    Preview.Builder().setTargetResolution(resolution).build().also {
                                        it.setSurfaceProvider(previewView!!.surfaceProvider)
                                    }

                            val analyzer = FrameAnalyzer(emitter, keyframeStore, stitchExecutor)
                            analyzer.setTiltSensor(tiltSensor)
                            frameAnalyzer = analyzer

                            analysisUseCase =
                                    ImageAnalysis.Builder()
                                            .setTargetResolution(resolution)
                                            .setBackpressureStrategy(
                                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                            )
                                            .build()
                                            .also { it.setAnalyzer(analyzerExecutor, analyzer) }

                            imageCapture =
                                    ImageCapture.Builder()
                                            .setCaptureMode(
                                                    ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                            )
                                            .build()

                            provider?.unbindAll()
                            provider?.bindToLifecycle(
                                    activity,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    previewUseCase,
                                    analysisUseCase,
                                    imageCapture,
                            )

                            call.resolve()
                        } catch (e: Exception) {
                            call.reject("Camera start failed: ${e.message}")
                        }
                    },
                    ContextCompat.getMainExecutor(context)
            )
        } // close runOnUiThread
    }

    fun stop() {
        activity.runOnUiThread {
            provider?.unbindAll()

            val parent = bridge.webView.parent as? ViewGroup
            previewView?.let { parent?.removeView(it) }
            previewView = null

            bridge.webView.setBackgroundColor(Color.WHITE)

            previewUseCase = null
            analysisUseCase = null
            imageCapture = null
            frameAnalyzer = null
        }
    }

    fun setPreviewVisible(visible: Boolean) {
        activity.runOnUiThread {
            previewUseCase?.setSurfaceProvider(if (visible) previewView?.surfaceProvider else null)
        }
    }

    fun setActiveSession(session: PanoramaSession?) {
        frameAnalyzer?.setSession(session)
    }

    fun setThrottled(throttled: Boolean) {
        frameAnalyzer?.setThrottled(throttled)
    }

    /** Binds the VideoCapture use-case to the already running camera provider. */
    fun bindVideoRecorder(recorder: VideoRecorder) {
        val p = provider ?: return
        recorder.bind(p, activity)
    }

    /**
     * Captures a still photo as a **manual keyframe** in `mode=manual` sessions.
     *
     * Unlike [captureStill] (repair frames), this method:
     * - Assigns the next sequential column index as the grid cell.
     * - Emits `keyframeAccepted` so the JS thumbnail strip updates immediately.
     * - Notifies [FrameAnalyzer] to update the overlap reference so subsequent
     *   `frame` events report overlap relative to the new frame (guide dot).
     * - Kicks off an incremental stitch preview when 2+ frames exist.
     */
    fun captureManualFrame(session: PanoramaSession, call: PluginCall) {
        val capture = imageCapture ?: run { call.reject("Camera not started"); return }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        image.close()

                        val rgbaMat = Mat()
                        org.opencv.android.Utils.bitmapToMat(bitmap, rgbaMat)
                        val bgrMat = Mat()
                        Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                        rgbaMat.release()

                        // Column = number of frames already accepted (0-based index)
                        val col = session.acceptedFrames.size
                        val saved = keyframeStore.save(session.sessionId, bgrMat)
                        session.frameMats.add(bgrMat.clone())
                        session.acceptedFrames.add(saved)

                        // Update overlap reference so next frame's overlapPct is
                        // relative to this newly captured frame (powers the guide dot).
                        frameAnalyzer?.notifyManualCapture(bgrMat)
                        bgrMat.release()

                        // Notify JS — thumbnail strip listens to this event.
                        emitter.emit(
                            "keyframeAccepted",
                            JSObject().apply {
                                put("frameId", saved.frameId)
                                put("thumbnailUri", saved.thumbnailUri)
                                put("fullUri", saved.fullUri)
                                put("gridCell", JSObject().apply {
                                    put("row", 0)
                                    put("col", col)
                                })
                                put("qualityScore", 1.0)
                                put("signals", JSObject().apply {
                                    put("blurScore", 0.0)
                                    put("motionMagnitude", 0.0)
                                    put("tiltDeg", 0.0)
                                    put("overlapPct", 0.0)
                                    put("lumaMean", 128.0)
                                    put("fps", 0.0)
                                    put("timestamp", System.currentTimeMillis().toDouble())
                                })
                            }
                        )

                        // Kick off incremental stitch preview (non-blocking).
                        if (session.frameMats.size >= 2) {
                            stitchExecutor.execute {
                                try {
                                    val result = session.stitchEngine.stitchIncrementalManual(session.frameMats)
                                    if (result.success && result.panorama != null) {
                                        val previewFile = java.io.File(
                                            context.filesDir,
                                            "shelfcam/sessions/${session.sessionId}/preview.jpg"
                                        ).also { it.parentFile?.mkdirs() }
                                        org.opencv.imgcodecs.Imgcodecs.imwrite(
                                            previewFile.absolutePath, result.panorama
                                        )
                                        result.panorama.release()
                                        emitter.emit("stitchProgress", JSObject().apply {
                                            put("sessionId", session.sessionId)
                                            put("completedCells", session.acceptedFrames.size)
                                            put("totalCells", session.acceptedFrames.size)
                                            put("previewUri", "file://${previewFile.absolutePath}")
                                            put("seamScore", result.seamScore.toDouble())
                                        })
                                    }
                                } catch (_: Exception) { /* non-fatal */ }
                            }
                        }

                        call.resolve(JSObject().apply {
                            put("frameId", saved.frameId)
                            put("fullUri", saved.fullUri)
                            put("thumbnailUri", saved.thumbnailUri)
                        })
                    } catch (e: Exception) {
                        call.reject("Manual capture failed: ${e.message}", "IO_ERROR")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    call.reject("Capture failed: ${exception.message}", "IO_ERROR")
                }
            }
        )
    }

    /**
     * Capture a full-resolution still for a repair frame or manual keyframe. Converts YUV → BGR
     * Mat, saves via [KeyframeStore], and resolves call with URIs.
     */
    fun captureStill(session: PanoramaSession, targetCell: JSObject?, call: PluginCall) {
        val capture =
                imageCapture
                        ?: run {
                            call.reject("Camera not started")
                            return
                        }
        capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bitmap = image.toBitmap()
                            image.close()

                            val rgbaMat = Mat()
                            org.opencv.android.Utils.bitmapToMat(bitmap, rgbaMat)
                            val bgrMat = Mat()
                            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
                            rgbaMat.release()

                            val saved = keyframeStore.save(session.sessionId, bgrMat)
                            session.addRepairFrame(saved, targetCell)
                            bgrMat.release()

                            call.resolve(
                                    JSObject().apply {
                                        put("frameId", saved.frameId)
                                        put("fullUri", saved.fullUri)
                                        put("thumbnailUri", saved.thumbnailUri)
                                    }
                            )
                        } catch (e: Exception) {
                            call.reject("Capture save failed: ${e.message}", "IO_ERROR")
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        call.reject("Capture failed: ${exception.message}", "IO_ERROR")
                    }
                }
        )
    }
}
