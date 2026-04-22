package com.efficy.shelfcamera.analyzers

import kotlin.math.abs
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB

data class OverlapMeasurement(
        val overlapPct: Float = 0f,
        val horizontalShiftPct: Float = 0f,
        val verticalShiftPct: Float = 0f,
        val confidencePct: Float = 0f,
)

class OverlapAnalyzer {

    private val orb = ORB.create(500)
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, true)

    private var prevKeyframeMat: Mat? = null
    private var prevKeyframeDesc: Mat? = null
    private var prevKeyframeKps: MatOfKeyPoint? = null
    private var frameCounter = 0
    private var prevMeasurement = OverlapMeasurement()

    fun analyze(lumaMat: Mat): OverlapMeasurement {
        frameCounter++
        if (frameCounter % 3 != 0) return prevMeasurement

        val refDesc = prevKeyframeDesc ?: return OverlapMeasurement()
        val refKps = prevKeyframeKps ?: return OverlapMeasurement()
        if (refDesc.empty()) return OverlapMeasurement()

        val kps = MatOfKeyPoint()
        val desc = Mat()
        val featureMask = Mat()
        val mask = Mat()
        val matchesList = MatOfDMatch()
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null
        var homography: Mat? = null

        return try {
            orb.detectAndCompute(lumaMat, featureMask, kps, desc)
            if (desc.empty()) return prevMeasurement

            matcher.match(refDesc, desc, matchesList)
            val matches = matchesList.toList()
            if (matches.size < 8) return prevMeasurement

            val refKpList = refKps.toList()
            val curKpList = kps.toList()

            srcPts = MatOfPoint2f(*matches.map { refKpList[it.queryIdx].pt }.toTypedArray())
            dstPts = MatOfPoint2f(*matches.map { curKpList[it.trainIdx].pt }.toTypedArray())

            homography = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 3.0, mask)
            if (mask.empty()) return prevMeasurement

            val inlierIndices =
                    (0 until mask.rows()).filter { mask.get(it, 0)[0] != 0.0 }
            if (inlierIndices.size < 6) return prevMeasurement

            val dxValues =
                    inlierIndices.map { index ->
                        curKpList[matches[index].trainIdx].pt.x - refKpList[matches[index].queryIdx].pt.x
                    }
            val dyValues =
                    inlierIndices.map { index ->
                        curKpList[matches[index].trainIdx].pt.y - refKpList[matches[index].queryIdx].pt.y
                    }

            val medianDxPx = median(dxValues).toFloat()
            val medianDyPx = median(dyValues).toFloat()
            val frameWidth = lumaMat.cols().coerceAtLeast(1).toFloat()
            val frameHeight = lumaMat.rows().coerceAtLeast(1).toFloat()
            val measured =
                    OverlapMeasurement(
                            overlapPct =
                                    (100f - abs(medianDxPx) / frameWidth * 100f).coerceIn(0f, 100f),
                            horizontalShiftPct =
                                    (medianDxPx / frameWidth * 100f).coerceIn(-100f, 100f),
                            verticalShiftPct =
                                    (medianDyPx / frameHeight * 100f).coerceIn(-100f, 100f),
                            confidencePct =
                                    (inlierIndices.size.toFloat() / matches.size.toFloat() * 100f)
                                            .coerceIn(0f, 100f),
                    )

            prevMeasurement =
                    if (prevMeasurement.confidencePct <= 0f) {
                        measured
                    } else {
                        OverlapMeasurement(
                                overlapPct = lerp(prevMeasurement.overlapPct, measured.overlapPct),
                                horizontalShiftPct =
                                        lerp(
                                                prevMeasurement.horizontalShiftPct,
                                                measured.horizontalShiftPct
                                        ),
                                verticalShiftPct =
                                        lerp(
                                                prevMeasurement.verticalShiftPct,
                                                measured.verticalShiftPct
                                        ),
                                confidencePct = measured.confidencePct,
                        )
                    }

            prevMeasurement
        } catch (_: Exception) {
            prevMeasurement
        } finally {
            kps.release()
            desc.release()
            featureMask.release()
            mask.release()
            matchesList.release()
            srcPts?.release()
            dstPts?.release()
            homography?.release()
        }
    }

    fun updateReference(keyframeMat: Mat?) {
        prevKeyframeMat?.release()
        prevKeyframeDesc?.release()
        prevKeyframeKps?.release()
        prevKeyframeMat = null
        prevKeyframeDesc = null
        prevKeyframeKps = null
        prevMeasurement = OverlapMeasurement()

        if (keyframeMat == null) return

        val clone = keyframeMat.clone()
        val kps = MatOfKeyPoint()
        val desc = Mat()
        val featureMask = Mat()
        orb.detectAndCompute(clone, featureMask, kps, desc)
        featureMask.release()

        prevKeyframeMat = clone
        prevKeyframeKps = kps
        prevKeyframeDesc = desc
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun lerp(previous: Float, next: Float): Float = previous * 0.65f + next * 0.35f
}
