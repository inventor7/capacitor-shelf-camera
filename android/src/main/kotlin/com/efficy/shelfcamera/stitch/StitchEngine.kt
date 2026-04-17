package com.efficy.shelfcamera.stitch

import android.util.Log
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Feature-based panorama stitcher.
 *
 * Why hand-rolled? The OpenCV Android SDK — including the official 4.12.0
 * release used here — ships the stitching algorithm as **C++ only**. There are
 * no JNI bindings for `org.opencv.stitching.Stitcher` in the Android SDK;
 * the Java wrapper was never generated for this module. The only way to call
 * stitching from Kotlin/Java on Android is therefore to build the pipeline
 * from the modules that DO have Java bindings:
 *
 *   - features2dfeatures2d  → ORB keypoints + descriptors, BFMatcher
 *   - calib3d     → findHomography (RANSAC), perspectiveTransform, warpPerspective
 *   - imgproc     → colour conversion, warpPerspective overload
 *   - core        → Mat arithmetic, gemm (matrix multiply for chain homography)
 *
 * Pipeline:
 *
 *   1. ORB keypoints + descriptors, per frame                 (features2d)
 *   2. Brute-force Hamming matcher + Lowe ratio filter        (features2d)
 *   3. Pairwise homography via RANSAC                         (calib3d)
 *   4. Chain homographies into a common reference (frame[0])
 *   5. Compute canvas bounds from warped frame corners
 *   6. warpPerspective each frame into the canvas             (calib3d/imgproc)
 *   7. "Last-write-wins" compositing under each frame's mask
 *
 * Robust to: empty input, single frame, one bad pair (clear abort with log).
 * Caps canvas area to [MAX_CANVAS_AREA_PX] so a runaway 360° sweep won't OOM.
 */
class StitchEngine {

    private val orb     = ORB.create(ORB_FEATURES)
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, false)

    /** Sliding-window size for [stitchIncremental] — fewer frames = faster preview. */
    private val slidingWindowSize = 4

    /** Full-pass stitch at commit time — produces the final shipped panorama. */
    fun stitchAll(frames: List<Mat>): StitchResult {
        if (frames.isEmpty()) {
            Log.w(TAG, "stitchAll called with empty frame list")
            return StitchResult(false, null, 0f)
        }
        if (frames.size == 1) {
            Log.i(TAG, "stitchAll: single frame, returning as-is")
            return StitchResult(true, frames[0].clone(), 1f)
        }
        return runStitch(frames, "FULL")
    }

    /**
     * Incremental preview stitch after each accepted keyframe.
     * Uses only the last [slidingWindowSize] frames to stay real-time.
     */
    fun stitchIncremental(frames: List<Mat>): StitchResult {
        if (frames.size < 2) return StitchResult(false, null, 0f)
        val window = frames.takeLast(slidingWindowSize)
        return runStitch(window, "INCR")
    }

    /** Replace the frame at [indexToReplace] and re-run [stitchAll]. */
    fun stitchReplacing(frames: MutableList<Mat>, indexToReplace: Int, newFrame: Mat): StitchResult {
        if (indexToReplace in frames.indices) {
            frames[indexToReplace].release()
            frames[indexToReplace] = newFrame
        } else {
            frames.add(newFrame)
        }
        return stitchAll(frames)
    }

    // ------------------------------------------------------------------------
    // Feature extraction
    // ------------------------------------------------------------------------

    private data class Features(val kps: MatOfKeyPoint, val desc: Mat) {
        fun release() {
            kps.release()
            desc.release()
        }
    }

    /** ORB keypoints+descriptors on a grayscale copy of `frame`. Null if no features. */
    private fun extractFeatures(frame: Mat): Features? {
        val gray = Mat()
        when (frame.channels()) {
            1 -> frame.copyTo(gray)
            3 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
            4 -> Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGRA2GRAY)
            else -> {
                gray.release()
                return null
            }
        }
        val kps = MatOfKeyPoint()
        val desc = Mat()
        orb.detectAndCompute(gray, Mat(), kps, desc)
        gray.release()
        if (desc.empty() || kps.empty()) {
            kps.release()
            desc.release()
            return null
        }
        return Features(kps, desc)
    }

    /**
     * Finds H such that points in [fA] map into [fB]'s coordinate frame.
     * Lowe ratio test + RANSAC. Returns null when the pair can't be aligned.
     */
    private fun findPairwiseHomography(fA: Features, fB: Features): Mat? {
        val knn = ArrayList<MatOfDMatch>()
        matcher.knnMatch(fA.desc, fB.desc, knn, 2)

        val good = knn.mapNotNull { mod ->
            val arr = mod.toArray()
            mod.release()
            if (arr.size < 2) return@mapNotNull null
            if (arr[0].distance < LOWE_RATIO * arr[1].distance) arr[0] else null
        }
        if (good.size < MIN_GOOD_MATCHES) {
            Log.w(TAG, "Too few good matches: ${good.size} < $MIN_GOOD_MATCHES")
            return null
        }

        val kpA = fA.kps.toList()
        val kpB = fB.kps.toList()
        val srcPts = MatOfPoint2f(*good.map { kpA[it.queryIdx].pt }.toTypedArray())
        val dstPts = MatOfPoint2f(*good.map { kpB[it.trainIdx].pt }.toTypedArray())
        val mask = Mat()

        val H = try {
            Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, RANSAC_REPROJ_PX, mask)
        } catch (e: Exception) {
            Log.w(TAG, "findHomography threw: ${e.message}")
            null
        }

        val inliers = if (!mask.empty()) {
            (0 until mask.rows()).count { mask.get(it, 0)[0] != 0.0 }
        } else 0

        srcPts.release()
        dstPts.release()
        mask.release()

        if (H == null || H.empty()) return null
        if (inliers < MIN_INLIERS) {
            Log.w(TAG, "Too few RANSAC inliers: $inliers < $MIN_INLIERS")
            H.release()
            return null
        }
        return H
    }

    // ------------------------------------------------------------------------
    // Core pipeline
    // ------------------------------------------------------------------------

    private fun runStitch(frames: List<Mat>, label: String): StitchResult {
        val startMs = System.currentTimeMillis()
        val features = mutableListOf<Features?>()
        val cumulativeH = mutableListOf<Mat>() // cumulativeH[i]: frame[i] pts → frame[0] pts

        try {
            // 1. Extract features
            for ((i, f) in frames.withIndex()) {
                val feats = extractFeatures(f) ?: run {
                    Log.w(TAG, "[$label] frame $i has no features — aborting")
                    return StitchResult(false, null, 0f)
                }
                features.add(feats)
            }

            // 2. Identity for reference frame
            cumulativeH.add(Mat.eye(3, 3, CvType.CV_64F))

            // 3. Chain pairwise homographies: H_i = H_1←0 · H_2←1 · … · H_i←i-1
            for (i in 1 until frames.size) {
                val pairH = findPairwiseHomography(features[i]!!, features[i - 1]!!) ?: run {
                    Log.w(TAG, "[$label] pair ${i - 1}→$i: homography failed")
                    return StitchResult(false, null, 0f)
                }
                val chained = Mat()
                // chained = cumulativeH[i-1] * pairH
                Core.gemm(cumulativeH[i - 1], pairH, 1.0, Mat(), 0.0, chained)
                pairH.release()
                cumulativeH.add(chained)
            }

            // 4. Canvas bounds
            val allCorners = ArrayList<Point>(frames.size * 4)
            for (i in frames.indices) {
                val f = frames[i]
                val w = f.width().toDouble()
                val h = f.height().toDouble()
                val corners = MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point(w,   0.0),
                    Point(w,   h),
                    Point(0.0, h),
                )
                val warped = MatOfPoint2f()
                Core.perspectiveTransform(corners, warped, cumulativeH[i])
                allCorners.addAll(warped.toList())
                corners.release()
                warped.release()
            }

            val minX = allCorners.minOf { it.x }
            val maxX = allCorners.maxOf { it.x }
            val minY = allCorners.minOf { it.y }
            val maxY = allCorners.maxOf { it.y }
            val canvasW = kotlin.math.ceil(maxX - minX).toInt().coerceAtLeast(1)
            val canvasH = kotlin.math.ceil(maxY - minY).toInt().coerceAtLeast(1)
            val canvasArea = canvasW.toLong() * canvasH.toLong()
            if (canvasArea > MAX_CANVAS_AREA_PX) {
                Log.w(TAG, "[$label] canvas too large: ${canvasW}x${canvasH} (area=$canvasArea)")
                return StitchResult(false, null, 0f)
            }

            // Translation so top-left corner sits at (0,0) in canvas space
            val translation = Mat.eye(3, 3, CvType.CV_64F).apply {
                put(0, 2, -minX)
                put(1, 2, -minY)
            }

            // 5. Warp frames into canvas
            val canvas = Mat.zeros(Size(canvasW.toDouble(), canvasH.toDouble()), frames[0].type())
            val canvasSize = canvas.size()

            for (i in frames.indices) {
                val warpH = Mat()
                Core.gemm(translation, cumulativeH[i], 1.0, Mat(), 0.0, warpH)

                val warped = Mat()
                Imgproc.warpPerspective(
                    frames[i], warped, warpH,
                    canvasSize,
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_CONSTANT,
                    Scalar(0.0, 0.0, 0.0, 0.0),
                )

                // Per-frame white mask warped the same way — defines "where did this
                // frame actually land on the canvas?" Prevents black border from
                // bleeding into already-filled regions.
                val frameMask = Mat(frames[i].size(), CvType.CV_8UC1, Scalar(255.0))
                val warpedMask = Mat()
                Imgproc.warpPerspective(
                    frameMask, warpedMask, warpH,
                    canvasSize,
                    Imgproc.INTER_NEAREST,
                    Core.BORDER_CONSTANT,
                    Scalar(0.0),
                )
                warped.copyTo(canvas, warpedMask)

                frameMask.release()
                warpedMask.release()
                warped.release()
                warpH.release()
            }

            translation.release()

            val seamScore = computeSeamScore(canvasW, frames)
            val elapsedMs = System.currentTimeMillis() - startMs
            Log.i(
                TAG,
                "[$label] ok: ${frames.size} frames → ${canvasW}x${canvasH}, " +
                    "seam=${"%.2f".format(seamScore)}, ${elapsedMs}ms",
            )
            return StitchResult(true, canvas, seamScore)
        } catch (e: Exception) {
            Log.e(TAG, "[$label] exception: ${e.javaClass.simpleName}: ${e.message}", e)
            return StitchResult(false, null, 0f)
        } finally {
            features.forEach { it?.release() }
            cumulativeH.forEach { it.release() }
        }
    }

    /**
     * Rough seam-quality heuristic in [0.5, 1.0].
     *
     * A good hand-held panorama has 30–50 % frame overlap, so the output
     * width is typically 0.5×–0.7× the sum of input widths. Ratio near 1.0
     * means frames concatenated with almost no overlap (weak seams); much
     * less than 0.5 means a tightly clumped pano. Peak at ratio = 0.6.
     * Placeholder — a real implementation would sample gradients along
     * predicted seams.
     */
    private fun computeSeamScore(canvasW: Int, inputs: List<Mat>): Float {
        val totalInputWidth = inputs.sumOf { it.width() }
        if (totalInputWidth <= 0) return 0f
        val ratio = (canvasW.toFloat() / totalInputWidth.toFloat()).coerceIn(0f, 1.2f)
        val distance = kotlin.math.abs(ratio - 0.6f)
        return (1f - distance).coerceIn(0.5f, 1f)
    }

    companion object {
        private const val TAG = "StitchEngine"
        private const val ORB_FEATURES = 1500
        private const val LOWE_RATIO = 0.75f
        private const val MIN_GOOD_MATCHES = 20
        private const val MIN_INLIERS = 10
        private const val RANSAC_REPROJ_PX = 3.0

        /** ~16 MP of canvas (e.g. 4000×4000 or 5300×3000) — stops runaway 360° sweeps. */
        private const val MAX_CANVAS_AREA_PX = 16L * 1_000L * 1_000L
    }
}
