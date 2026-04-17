package com.efficy.shelfcamera.analyzers

import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB

/**
 * Computes % overlap between the current frame and the last accepted keyframe
 * using ORB feature matching + RANSAC homography inlier ratio.
 *
 * Runs every 3rd call to spare CPU (~3.3 Hz when frames arrive at 10 Hz).
 *
 * Mat lifecycle: all OpenCV Mats allocated in [analyze] are released in the
 * `finally` block. Reference Mats held across calls are replaced/released in
 * [updateReference].
 */
class OverlapAnalyzer {

    private val orb     = ORB.create(500)
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, true)

    private var prevKeyframeMat:  Mat? = null
    private var prevKeyframeDesc: Mat? = null
    private var prevKeyframeKps:  MatOfKeyPoint? = null
    private var frameCounter = 0
    private var prevOverlapPct = 0f

    /**
     * @param lumaMat current grayscale frame (CV_8UC1)
     * @return overlap percentage [0..100]; 0 if no reference yet or too few matches.
     */
    fun analyze(lumaMat: Mat): Float {
        frameCounter++
        if (frameCounter % 3 != 0) return prevOverlapPct

        // Fast-path: nothing to compare against — skip ORB entirely.
        val refDesc = prevKeyframeDesc ?: return 0f
        val refKps  = prevKeyframeKps ?: return 0f
        if (refDesc.empty()) return 0f

        val kps = MatOfKeyPoint()
        val desc = Mat()
        val mask = Mat()
        val matchesList = MatOfDMatch()
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null

        return try {
            orb.detectAndCompute(lumaMat, Mat(), kps, desc)
            if (desc.empty()) return 0f

            matcher.match(refDesc, desc, matchesList)
            val matches = matchesList.toList()
            if (matches.size < 8) return 0f

            val refKpList = refKps.toList()
            val curKpList = kps.toList()

            srcPts = MatOfPoint2f(*matches.map { refKpList[it.queryIdx].pt }.toTypedArray())
            dstPts = MatOfPoint2f(*matches.map { curKpList[it.trainIdx].pt }.toTypedArray())

            Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 3.0, mask)
            if (mask.empty()) return 0f

            val inliers = (0 until mask.rows()).count { mask.get(it, 0)[0] != 0.0 }
            prevOverlapPct = (inliers.toFloat() / matches.size * 100f).coerceIn(0f, 100f)
            prevOverlapPct
        } catch (e: Exception) {
            // Overlap failure is non-fatal — just return the last known value.
            0f
        } finally {
            kps.release()
            desc.release()
            mask.release()
            matchesList.release()
            srcPts?.release()
            dstPts?.release()
        }
    }

    /**
     * Called when a keyframe is accepted — updates the reference frame.
     * Pass `null` to reset (e.g. at the start of a new session).
     */
    fun updateReference(keyframeMat: Mat?) {
        prevKeyframeMat?.release()
        prevKeyframeDesc?.release()
        prevKeyframeKps?.release()
        prevKeyframeMat  = null
        prevKeyframeDesc = null
        prevKeyframeKps  = null
        prevOverlapPct   = 0f

        if (keyframeMat == null) return

        val clone = keyframeMat.clone()
        val kps = MatOfKeyPoint()
        val desc = Mat()
        orb.detectAndCompute(clone, Mat(), kps, desc)

        prevKeyframeMat  = clone
        prevKeyframeKps  = kps
        prevKeyframeDesc = desc
    }
}
