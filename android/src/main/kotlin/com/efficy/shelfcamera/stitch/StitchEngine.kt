package com.efficy.shelfcamera.stitch

import org.opencv.core.Mat
import org.opencv.stitching.Stitcher

/**
 * Wraps OpenCV Stitcher to provide full-pass and incremental panorama stitching.
 */
class StitchEngine {

    private val slidingWindowSize = 4

    /**
     * Full-pass stitch of all frames. Used at [commitPanorama].
     */
    fun stitchAll(frames: List<Mat>): StitchResult {
        if (frames.size < 2) return StitchResult(success = false, panorama = null, seamScore = 0f)
        return runStitch(frames)
    }

    /**
     * Incremental stitch using a sliding window of the last N frames.
     * Faster than full-pass; used after each keyframe acceptance.
     */
    fun stitchIncremental(frames: List<Mat>): StitchResult {
        if (frames.size < 2) return StitchResult(success = false, panorama = null, seamScore = 0f)
        val window = frames.takeLast(slidingWindowSize)
        return runStitch(window)
    }

    /**
     * Replace a specific frame by index and re-stitch the full list.
     */
    fun stitchReplacing(frames: MutableList<Mat>, indexToReplace: Int, newFrame: Mat): StitchResult {
        if (indexToReplace in frames.indices) {
            frames[indexToReplace].release()
            frames[indexToReplace] = newFrame
        } else {
            frames.add(newFrame)
        }
        return stitchAll(frames)
    }

    private fun runStitch(frames: List<Mat>): StitchResult {
        val stitcher = Stitcher.create(Stitcher.PANORAMA)
        stitcher.setPanoConfidenceThresh(0.3)
        val out = Mat()
        return when (stitcher.stitch(frames, out)) {
            Stitcher.OK -> StitchResult(success = true, panorama = out, seamScore = computeSeamScore(out))
            else        -> { out.release(); StitchResult(success = false, panorama = null, seamScore = 0f) }
        }
    }

    /**
     * Heuristic seam score: ratio of non-black pixels in the output panorama.
     * A fully stitched result with few seam artefacts will have a high ratio.
     */
    private fun computeSeamScore(pano: Mat): Float {
        val nonZero = org.opencv.core.Core.countNonZero(
            pano.reshape(1, pano.rows() * pano.cols()),
        )
        val total = pano.rows() * pano.cols() * pano.channels()
        return if (total == 0) 0f else (nonZero.toFloat() / total).coerceIn(0f, 1f)
    }
}
