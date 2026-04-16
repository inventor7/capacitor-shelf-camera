package com.efficy.shelfcamera.analyzers

import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB

/**
 * Computes % overlap between current frame and last accepted keyframe
 * using ORB feature matching + RANSAC homography inlier ratio.
 * Only invoked every 3rd frame (~10 Hz) to spare CPU.
 */
class OverlapAnalyzer {

    private val orb     = ORB.create(500)
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, true)
    private var prevKeyframeMat: Mat? = null
    private var prevKeyframeDesc: Mat? = null
    private var prevKeyframeKps: MatOfKeyPoint? = null
    private var frameCounter = 0

    /**
     * @param lumaMat current grayscale frame
     * @return overlap percentage [0..100]; -1 if no reference frame yet
     */
    fun analyze(lumaMat: Mat): Float {
        frameCounter++
        if (frameCounter % 3 != 0) return prevOverlapPct

        val kps  = MatOfKeyPoint()
        val desc = Mat()
        orb.detectAndCompute(lumaMat, Mat(), kps, desc)

        val ref     = prevKeyframeMat
        val refDesc = prevKeyframeDesc
        val refKps  = prevKeyframeKps

        if (ref == null || refDesc == null || refKps == null || desc.empty() || refDesc.empty()) {
            return 0f
        }

        val matchesList = MatOfDMatch()
        matcher.match(refDesc, desc, matchesList)

        val matches = matchesList.toList()
        if (matches.size < 8) return 0f

        val srcPts = MatOfPoint2f(*matches.map { m ->
            refKps.toList()[m.queryIdx].pt
        }.toTypedArray())
        val dstPts = MatOfPoint2f(*matches.map { m ->
            kps.toList()[m.trainIdx].pt
        }.toTypedArray())

        val mask = Mat()
        Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 3.0, mask)

        val inliers = (0 until mask.rows()).count { mask.get(it, 0)[0] != 0.0 }
        prevOverlapPct = (inliers.toFloat() / matches.size * 100f).coerceIn(0f, 100f)
        return prevOverlapPct
    }

    /** Call when a keyframe is accepted to update the reference, or null to reset. */
    fun updateReference(keyframeMat: Mat?) {
        prevKeyframeMat?.release()
        prevKeyframeDesc?.release()
        prevKeyframeKps = null
        prevOverlapPct = 0f

        if (keyframeMat == null) {
            prevKeyframeMat = null
            prevKeyframeDesc = null
            return
        }

        prevKeyframeMat = keyframeMat.clone()

        val kps  = MatOfKeyPoint()
        val desc = Mat()
        orb.detectAndCompute(prevKeyframeMat, Mat(), kps, desc)
        prevKeyframeDesc = desc
        prevKeyframeKps  = kps
    }

    private var prevOverlapPct = 0f
}
