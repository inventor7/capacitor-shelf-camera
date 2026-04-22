package com.efficy.shelfcamera

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.efficy.shelfcamera.analyzers.FrameAnalyzer
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.sensors.TiltSensor
import com.efficy.shelfcamera.stitch.ManualFrameHint
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
    private data class PreviewFrameSpec(
            val xPx: Int,
            val yPx: Int,
            val widthPx: Int,
            val heightPx: Int,
    )

    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val stitchExecutor = Executors.newSingleThreadExecutor()
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var frameAnalyzer: FrameAnalyzer? = null
    private var previewFrameSpec: PreviewFrameSpec? = null
    private val keyframeStore = KeyframeStore(context)

    fun start(resolution: Size, call: PluginCall) {
        activity.runOnUiThread {
            bridge.webView.setBackgroundColor(Color.TRANSPARENT)
            val parent = bridge.webView.parent as? ViewGroup

            previewView =
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        alpha = 0f
                        visibility = View.INVISIBLE
                        layoutParams =
                                ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                )
                    }
            parent?.addView(previewView, 0) // add underneath WebView
            applyPreviewFrame()

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
                                            .setTargetResolution(resolution)
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

    fun setPreviewFrame(x: Double, y: Double, width: Double, height: Double) {
        val density = context.resources.displayMetrics.density.toDouble()
        previewFrameSpec =
                PreviewFrameSpec(
                        xPx = (x * density).toInt(),
                        yPx = (y * density).toInt(),
                        widthPx = (width * density).toInt(),
                        heightPx = (height * density).toInt(),
                )
        activity.runOnUiThread { applyPreviewFrame() }
    }

    fun setActiveSession(session: PanoramaSession?) {
        frameAnalyzer?.setSession(session)
    }

    fun setThrottled(throttled: Boolean) {
        frameAnalyzer?.setThrottled(throttled)
    }

    private fun applyPreviewFrame() {
        val frame = previewFrameSpec ?: return
        val view = previewView ?: return
        val params =
                view.layoutParams
                        ?: ViewGroup.LayoutParams(frame.widthPx, frame.heightPx)
        params.width = frame.widthPx
        params.height = frame.heightPx
        view.layoutParams = params
        view.x = frame.xPx.toFloat()
        view.y = frame.yPx.toFloat()
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    fun captureManualFrame(session: PanoramaSession, call: PluginCall) {
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

                            val signals = frameAnalyzer?.latestSignalSnapshot()
                            val col = session.acceptedFrames.size
                            val saved = keyframeStore.save(session.sessionId, bgrMat)
                            session.frameMats.add(bgrMat.clone())
                            session.acceptedFrames.add(saved)
                            session.manualFrameHints.add(
                                    if (col == 0) {
                                        ManualFrameHint()
                                    } else {
                                        ManualFrameHint(
                                                horizontalShiftPct =
                                                        signals?.horizontalShiftPct ?: 0f,
                                                verticalShiftPct =
                                                        signals?.verticalShiftPct ?: 0f,
                                        )
                                    }
                            )
                            frameAnalyzer?.notifyManualCapture(bgrMat)
                            bgrMat.release()

                            val blurScore = 0f
                            val motionMagnitude = 0f
                            val tiltDeg = signals?.tiltDeg ?: 0f
                            val rollDeg = signals?.rollDeg ?: 0f
                            val levelOffsetX = signals?.levelOffsetX ?: 0f
                            val levelOffsetY = signals?.levelOffsetY ?: 0f
                            val overlapPct = signals?.overlapPct ?: 0f
                            val horizontalShiftPct = signals?.horizontalShiftPct ?: 0f
                            val verticalShiftPct = signals?.verticalShiftPct ?: 0f
                            val overlapConfidencePct = signals?.overlapConfidencePct ?: 0f
                            val lumaMean = 0f
                            val fps = signals?.fps ?: 0f
                            val timestamp = signals?.timestamp ?: System.currentTimeMillis()
                            val qualityScore = 1f

                            emitter.emit(
                                    "keyframeAccepted",
                                    JSObject().apply {
                                        put("frameId", saved.frameId)
                                        put("thumbnailUri", saved.thumbnailUri)
                                        put("fullUri", saved.fullUri)
                                        put(
                                                "gridCell",
                                                JSObject().apply {
                                                    put("row", 0)
                                                    put("col", col)
                                                }
                                        )
                                        put("qualityScore", qualityScore.toDouble())
                                        put(
                                                "signals",
                                                JSObject().apply {
                                                    put("blurScore", blurScore.toDouble())
                                                    put("motionMagnitude", motionMagnitude.toDouble())
                                                    put("tiltDeg", tiltDeg.toDouble())
                                                    put("rollDeg", rollDeg.toDouble())
                                                    put("levelOffsetX", levelOffsetX.toDouble())
                                                    put("levelOffsetY", levelOffsetY.toDouble())
                                                    put("overlapPct", overlapPct.toDouble())
                                                    put(
                                                            "horizontalShiftPct",
                                                            horizontalShiftPct.toDouble()
                                                    )
                                                    put(
                                                            "verticalShiftPct",
                                                            verticalShiftPct.toDouble()
                                                    )
                                                    put(
                                                            "overlapConfidencePct",
                                                            overlapConfidencePct.toDouble()
                                                    )
                                                    put("lumaMean", lumaMean.toDouble())
                                                    put("fps", fps.toDouble())
                                                    put("timestamp", timestamp)
                                                }
                                        )
                                    }
                            )

                            if (session.frameMats.size >= 2) {
                                stitchExecutor.execute {
                                    try {
                                        val result =
                                                session.stitchEngine.stitchIncrementalManual(
                                                        session.frameMats
                                                )
                                        if (result.success && result.panorama != null) {
                                            val previewFile =
                                                    java.io.File(
                                                                    keyframeStore.sessionDir(
                                                                            session.sessionId
                                                                    ),
                                                                    "preview.jpg"
                                                            )
                                                            .also {
                                                                it.parentFile?.mkdirs()
                                                            }
                                            org.opencv.imgcodecs.Imgcodecs.imwrite(
                                                    previewFile.absolutePath,
                                                    result.panorama
                                            )
                                            result.panorama.release()

                                            emitter.emit(
                                                    "stitchProgress",
                                                    JSObject().apply {
                                                        put("sessionId", session.sessionId)
                                                        put(
                                                                "completedCells",
                                                                session.acceptedFrames.size
                                                        )
                                                        put(
                                                                "totalCells",
                                                                session.expectedCellCount
                                                                        ?: session.acceptedFrames.size
                                                        )
                                                        put(
                                                                "previewUri",
                                                                "file://${previewFile.absolutePath}"
                                                        )
                                                        put(
                                                                "seamScore",
                                                                result.seamScore.toDouble()
                                                        )
                                                    }
                                            )
                                        }
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            call.resolve(
                                    JSObject().apply {
                                        put("frameId", saved.frameId)
                                        put("fullUri", saved.fullUri)
                                        put("thumbnailUri", saved.thumbnailUri)
                                    }
                            )
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
