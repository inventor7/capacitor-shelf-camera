package com.efficy.shelfcamera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.efficy.shelfcamera.keyframe.KeyframeDecider
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.util.EventEmitter
import com.getcapacitor.JSObject
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * Decodes frames from a recorded video file and feeds them through
 * [KeyframeDecider] — reusing the same quality gates as the live sweep.
 *
 * Frame extraction rate: one frame every 333 ms (~3 fps) to match typical
 * shelf-sweep speed without decoding every frame and saturating memory.
 */
class VideoFrameExtractor(
        private val context: Context,
        private val emitter: EventEmitter,
        private val keyframeStore: KeyframeStore,
        private val executor: ExecutorService,
) {
    private val TAG = "VideoFrameExtractor"
    private val frameSampleIntervalUs = 333_000L // 333 ms in microseconds

    fun process(
            session: PanoramaSession,
            videoUri: String,
            thresholds: KeyframeDecider.Thresholds?,
            onDone: () -> Unit,
            onError: (String) -> Unit,
    ) {
        executor.execute {
            val decider = KeyframeDecider(
                    minBlur = thresholds?.minBlur ?: 0.35f,
                    maxMotion = thresholds?.maxMotion ?: 0.35f,
                    maxTiltDeg = thresholds?.maxTiltDeg ?: 20f,
                    minOverlapPct = thresholds?.minOverlapPct ?: 20f,
            )

            // Strip file:// prefix if present
            val filePath = videoUri.removePrefix("file://")
            val file = File(filePath)
            if (!file.exists()) {
                onError("Video file not found: $filePath")
                return@execute
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: run {
                    onError("Cannot read video duration")
                    return@execute
                }
                val durationUs = durationMs * 1000L

                emitter.emit("videoProgress", JSObject().apply {
                    put("sessionId", session.sessionId)
                    put("extractedFrames", 0)
                    put("acceptedFrames", 0)
                    put("phase", "extracting")
                })

                var posUs = 0L
                var extractedFrames = 0
                var acceptedFrames = 0

                while (posUs <= durationUs && !session.isCancelled) {
                    val bitmap: Bitmap? = retriever.getFrameAtTime(
                            posUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )

                    if (bitmap != null) {
                        extractedFrames++
                        val bgrMat = bitmapToBgr(bitmap)
                        bitmap.recycle()

                        // Synthesize signals from the Mat for KeyframeDecider
                        val blurScore = estimateBlur(bgrMat)
                        val signals = KeyframeDecider.Signals(
                                blurScore = blurScore,
                                motionMagnitude = 0f, // Video frames are static captures
                                tiltDeg = 0f,         // Tilt unknown from recorded video
                                overlapPct = if (acceptedFrames == 0) 50f else 30f, // assumed
                                lumaMean = estimateLuma(bgrMat),
                                timestampMs = posUs / 1000L,
                        )

                        if (decider.evaluate(signals)) {
                            val saved = keyframeStore.save(session.sessionId, bgrMat)
                            session.addVideoFrame(saved)
                            acceptedFrames++

                            emitter.emit("keyframeAccepted", JSObject().apply {
                                put("frameId", saved.frameId)
                                put("thumbnailUri", saved.thumbnailUri)
                                put("fullUri", saved.fullUri)
                                put("gridCell", JSObject().apply {
                                    put("row", 0)
                                    put("col", acceptedFrames - 1)
                                })
                                put("qualityScore", blurScore.toDouble())
                                put("signals", JSObject().apply {
                                    put("blurScore", blurScore.toDouble())
                                    put("motionMagnitude", 0.0)
                                    put("tiltDeg", 0.0)
                                    put("overlapPct", signals.overlapPct.toDouble())
                                    put("lumaMean", signals.lumaMean.toDouble())
                                    put("fps", 3.0)
                                    put("timestamp", posUs / 1000L)
                                })
                            })
                        }

                        bgrMat.release()

                        emitter.emit("videoProgress", JSObject().apply {
                            put("sessionId", session.sessionId)
                            put("extractedFrames", extractedFrames)
                            put("acceptedFrames", acceptedFrames)
                            put("phase", "extracting")
                        })
                    }

                    posUs += frameSampleIntervalUs
                }

                Log.i(TAG, "Extraction done: $extractedFrames decoded, $acceptedFrames accepted")

                if (acceptedFrames < 2) {
                    onError("Not enough quality frames extracted from video ($acceptedFrames). Try a slower, steadier sweep.")
                    return@execute
                }

                emitter.emit("videoProgress", JSObject().apply {
                    put("sessionId", session.sessionId)
                    put("extractedFrames", extractedFrames)
                    put("acceptedFrames", acceptedFrames)
                    put("phase", "stitching")
                })

                onDone()

            } catch (e: Exception) {
                Log.e(TAG, "Video processing failed", e)
                onError("Video processing error: ${e.message}")
            } finally {
                retriever.release()
            }
        }
    }

    private fun bitmapToBgr(bitmap: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
    }

    /** Laplacian variance as a blur estimate (mirrors BlurAnalyzer logic). */
    private fun estimateBlur(bgr: Mat): Float {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        val lap = Mat()
        Imgproc.Laplacian(gray, lap, org.opencv.core.CvType.CV_64F)
        val mean = org.opencv.core.Core.meanStdDev(lap, org.opencv.core.MatOfDouble(), org.opencv.core.MatOfDouble())
        val stdDev = org.opencv.core.MatOfDouble()
        val stdMean = org.opencv.core.MatOfDouble()
        org.opencv.core.Core.meanStdDev(lap, stdMean, stdDev)
        val variance = (stdDev.toArray().firstOrNull() ?: 0.0).coerceIn(0.0, 1000.0)
        gray.release(); lap.release()
        stdDev.release(); stdMean.release()
        return (variance / 1000.0).toFloat().coerceIn(0f, 1f)
    }

    private fun estimateLuma(bgr: Mat): Float {
        val meanScalar = org.opencv.core.Core.mean(bgr)
        // Approximate luma from BGR: 0.114*B + 0.587*G + 0.299*R
        return (0.114 * meanScalar.`val`[0] + 0.587 * meanScalar.`val`[1] + 0.299 * meanScalar.`val`[2]).toFloat()
    }
}
