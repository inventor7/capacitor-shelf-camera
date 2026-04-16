package com.efficy.shelfcamera

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.efficy.shelfcamera.analyzers.FrameAnalyzer
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.sensors.TiltSensor
import com.efficy.shelfcamera.util.EventEmitter
import com.efficy.shelfcamera.util.toYuvMat
import com.getcapacitor.JSObject
import com.getcapacitor.PluginCall
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Owns the CameraX lifecycle: binds Preview + ImageAnalysis + ImageCapture use-cases
 * and routes frames to [FrameAnalyzer] for per-frame coaching signals.
 */
class CameraController(
    private val context: Context,
    private val activity: FragmentActivity,
    private val emitter: EventEmitter,
    private val tiltSensor: TiltSensor,
) {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val stitchExecutor = Executors.newSingleThreadExecutor()
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var provider: ProcessCameraProvider? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var frameAnalyzer: FrameAnalyzer? = null
    private val keyframeStore = KeyframeStore(context)

    fun start(resolution: Size, call: PluginCall) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get()

                previewUseCase = Preview.Builder()
                    .setTargetResolution(resolution)
                    .build()
                    .also {
                        currentSurfaceProvider?.let { sp -> it.setSurfaceProvider(sp) }
                    }

                val analyzer = FrameAnalyzer(emitter, keyframeStore, stitchExecutor)
                analyzer.setTiltSensor(tiltSensor)
                frameAnalyzer = analyzer

                analysisUseCase = ImageAnalysis.Builder()
                    .setTargetResolution(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analyzerExecutor, analyzer) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider?.unbindAll()
                provider?.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase, analysisUseCase, imageCapture,
                )

                call.resolve()
            } catch (e: Exception) {
                call.reject("Camera start failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        provider?.unbindAll()
        previewUseCase = null
        analysisUseCase = null
        imageCapture = null
        frameAnalyzer = null
    }

    fun setPreviewVisible(visible: Boolean) {
        previewUseCase?.setSurfaceProvider(if (visible) currentSurfaceProvider else null)
    }

    fun setActiveSession(session: PanoramaSession?) {
        frameAnalyzer?.setSession(session)
    }

    fun setThrottled(throttled: Boolean) {
        frameAnalyzer?.setThrottled(throttled)
    }

    /**
     * Capture a full-resolution still for a repair frame or manual keyframe.
     * Converts YUV → BGR Mat, saves via [KeyframeStore], and resolves call with URIs.
     */
    fun captureStill(session: PanoramaSession, targetCell: JSObject?, call: PluginCall) {
        val capture = imageCapture ?: run {
            call.reject("Camera not started")
            return
        }
        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val lumaMat = image.toYuvMat()
                        image.close()
                        if (lumaMat == null) {
                            call.reject("IO_ERROR", "Failed to convert captured image")
                            return
                        }

                        val bgrMat = Mat()
                        Imgproc.cvtColor(lumaMat, bgrMat, Imgproc.COLOR_GRAY2BGR)
                        lumaMat.release()

                        val saved = keyframeStore.save(session.sessionId, bgrMat)
                        session.addRepairFrame(saved, targetCell)
                        bgrMat.release()

                        call.resolve(JSObject().apply {
                            put("frameId", saved.frameId)
                            put("fullUri", saved.fullUri)
                            put("thumbnailUri", saved.thumbnailUri)
                        })
                    } catch (e: Exception) {
                        call.reject("IO_ERROR", "Capture save failed: ${e.message}")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    call.reject("IO_ERROR", "Capture failed: ${exception.message}")
                }
            }
        )
    }
}
