package com.efficy.shelfcamera.analyzers

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.efficy.shelfcamera.PanoramaSession
import com.efficy.shelfcamera.keyframe.KeyframeDecider
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.sensors.TiltSensor
import com.efficy.shelfcamera.util.EventEmitter
import com.efficy.shelfcamera.util.toYuvMat
import com.getcapacitor.JSObject
import java.util.concurrent.ExecutorService
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * ImageAnalysis.Analyzer orchestrator: pulls luma Mat from each frame, runs blur + motion + reads
 * tilt + luma-mean, aggregates CoachingSignals, and emits via [EventEmitter] throttled to 10 Hz.
 *
 * When a [PanoramaSession] is active, evaluates keyframe acceptance via [KeyframeDecider] and
 * triggers incremental stitching.
 */
class FrameAnalyzer(
        private val emitter: EventEmitter,
        private val keyframeStore: KeyframeStore,
        private val stitchExecutor: ExecutorService,
) : ImageAnalysis.Analyzer {

    private val blurAnalyzer = BlurAnalyzer()
    private val motionAnalyzer = MotionAnalyzer()
    private val overlapAnalyzer = OverlapAnalyzer()
    private var tiltSensor: TiltSensor? = null
    private var session: PanoramaSession? = null

    private var lastEmitMs = 0L
    private var frameCount = 0
    private var lastFpsMs = System.currentTimeMillis()
    private var fps = 0f

    private var emitIntervalMs = 100L // 10 Hz normal, 200L when throttled
    private var isThrottled = false

    fun setSession(s: PanoramaSession?) {
        session = s
        if (s != null) {
            overlapAnalyzer.updateReference(null)
        }
    }

    fun setTiltSensor(sensor: TiltSensor) {
        tiltSensor = sensor
    }

    fun setThrottled(throttled: Boolean) {
        isThrottled = throttled
        emitIntervalMs = if (throttled) 200L else 100L // 5 Hz vs 10 Hz
    }

    override fun analyze(image: ImageProxy) {
        try {
            val mat = image.toYuvMat() ?: return

            val now = System.currentTimeMillis()
            frameCount++
            val elapsed = now - lastFpsMs
            if (elapsed >= 1000L) {
                fps = frameCount / (elapsed / 1000f)
                frameCount = 0
                lastFpsMs = now
            }

            if (now - lastEmitMs < emitIntervalMs) {
                mat.release()
                return
            }
            lastEmitMs = now

            val blurScore = blurAnalyzer.analyze(mat)
            val motionMagnitude = motionAnalyzer.analyze(mat)
            val tiltDeg = tiltSensor?.currentTiltDeg ?: 0f
            val lumaMean = computeLumaMean(mat)
            val overlapPct = overlapAnalyzer.analyze(mat)

            val activeSession = session
            if (activeSession != null && !activeSession.isCancelled) {
                evaluateKeyframe(
                        activeSession,
                        blurScore,
                        motionMagnitude,
                        tiltDeg,
                        overlapPct,
                        lumaMean,
                        now,
                        image,
                        mat
                )
            } else {
                mat.release()
            }

            val signals =
                    JSObject().apply {
                        put("blurScore", blurScore.toDouble())
                        put("motionMagnitude", motionMagnitude.toDouble())
                        put("tiltDeg", tiltDeg.toDouble())
                        put("overlapPct", overlapPct.toDouble())
                        put("lumaMean", lumaMean.toDouble())
                        put("fps", fps.toDouble())
                        put("timestamp", now)
                        activeSession?.keyframeDecider?.lastRejectionReason?.let {
                            put("rejectionReason", it)
                        }
                    }

            emitter.emit("frame", signals)
        } finally {
            image.close()
        }
    }

    /**
     * Evaluates whether the current frame qualifies as a keyframe. If accepted: saves to disk,
     * emits `keyframeAccepted`, triggers incremental stitch.
     */
    private fun evaluateKeyframe(
            session: PanoramaSession,
            blur: Float,
            motion: Float,
            tilt: Float,
            overlap: Float,
            luma: Float,
            nowMs: Long,
            image: ImageProxy,
            lumaMat: Mat,
    ) {
        val accepted =
                session.keyframeDecider.evaluate(
                        KeyframeDecider.Signals(
                                blurScore = blur,
                                motionMagnitude = motion,
                                tiltDeg = tilt,
                                overlapPct = overlap,
                                lumaMean = luma,
                                timestampMs = nowMs,
                        )
                )

        if (!accepted) {
            lumaMat.release()
            return
        }

        // Convert full ImageProxy to BGR for storage and stitching (provides full color, rotation,
        // and crop)
        val bitmap = image.toBitmap()
        val rgbaMat = Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, rgbaMat)
        val bgrMat = Mat()
        Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)
        rgbaMat.release()
        lumaMat.release()

        // Save to disk
        val saved = keyframeStore.save(session.sessionId, bgrMat)

        // Update overlap reference
        overlapAnalyzer.updateReference(bgrMat)

        // Infer grid cell
        val (row, col) = session.gridInferrer.cell
        // TODO: feed actual translation vector from OverlapAnalyzer homography
        session.gridInferrer.update(0f, 0f)

        // Keep Mat in memory for final stitch
        session.frameMats.add(bgrMat.clone())
        session.acceptedFrames.add(saved)

        bgrMat.release()

        // Compute composite quality score
        val qualityScore =
                (blur * 0.4f + (1f - motion) * 0.3f + (1f - tilt / 90f).coerceIn(0f, 1f) * 0.3f)
                        .coerceIn(0f, 1f)

        // Emit keyframeAccepted
        emitter.emit(
                "keyframeAccepted",
                JSObject().apply {
                    put("frameId", saved.frameId)
                    put("thumbnailUri", saved.thumbnailUri)
                    put("fullUri", saved.fullUri)
                    put(
                            "gridCell",
                            JSObject().apply {
                                put("row", row)
                                put("col", col)
                            }
                    )
                    put("qualityScore", qualityScore.toDouble())
                    put(
                            "signals",
                            JSObject().apply {
                                put("blurScore", blur.toDouble())
                                put("motionMagnitude", motion.toDouble())
                                put("tiltDeg", tilt.toDouble())
                                put("overlapPct", overlap.toDouble())
                                put("lumaMean", luma.toDouble())
                                put("fps", fps.toDouble())
                                put("timestamp", nowMs)
                            }
                    )
                }
        )

        // Skip incremental stitching when thermally throttled — only final stitchAll at commit
        if (session.frameMats.size >= 2 && !isThrottled) {
            stitchExecutor.execute {
                try {
                    val result = session.stitchEngine.stitchIncremental(session.frameMats)
                    if (result.success && result.panorama != null) {
                        // Save incremental preview
                        val previewFile =
                                java.io.File(
                                        keyframeStore.sessionDir(session.sessionId),
                                        "preview.jpg"
                                )
                        previewFile.parentFile?.mkdirs()
                        org.opencv.imgcodecs.Imgcodecs.imwrite(
                                previewFile.absolutePath,
                                result.panorama
                        )
                        result.panorama.release()

                        emitter.emit(
                                "stitchProgress",
                                JSObject().apply {
                                    put("sessionId", session.sessionId)
                                    put("completedCells", session.acceptedFrames.size)
                                    put(
                                            "totalCells",
                                            session.acceptedFrames.size
                                    ) // inferred at commit
                                    put("previewUri", "file://${previewFile.absolutePath}")
                                    put("seamScore", result.seamScore.toDouble())
                                }
                        )
                    }
                } catch (_: Exception) {
                    // Non-fatal: incremental stitch failure doesn't block capture
                }
            }
        }
    }

    private fun computeLumaMean(mat: Mat): Float {
        val mean = Core.mean(mat)
        return mean.`val`[0].toFloat()
    }
}
