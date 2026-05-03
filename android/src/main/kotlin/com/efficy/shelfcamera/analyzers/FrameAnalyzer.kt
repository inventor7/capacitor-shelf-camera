package com.efficy.shelfcamera.analyzers

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.efficy.shelfcamera.PanoramaSession
import com.efficy.shelfcamera.util.EventEmitter
import com.efficy.shelfcamera.util.toYuvMat
import com.getcapacitor.JSObject
import org.opencv.core.Mat

data class FrameSignalSnapshot(
        val overlapPct: Float,
        val timestamp: Long,
) {
    fun toJsObject(): JSObject =
            JSObject().apply {
                put("overlapPct", overlapPct.toDouble())
                put("timestamp", timestamp)
            }
}

class FrameAnalyzer(
        private val emitter: EventEmitter,
) : ImageAnalysis.Analyzer {

    private val overlapAnalyzer = OverlapAnalyzer()
    private var session: PanoramaSession? = null

    private var lastEmitMs = 0L
    private var emitIntervalMs = 90L
    private var latestSignals: FrameSignalSnapshot? = null
    private var latestOverlapMeasurement = OverlapMeasurement()

    fun setSession(s: PanoramaSession?) {
        session = s
        overlapAnalyzer.updateReference(s?.frameMats?.lastOrNull())
    }

    fun setThrottled(throttled: Boolean) {
        emitIntervalMs = if (throttled) 160L else 90L
    }

    fun notifyManualCapture(frameMat: Mat) {
        overlapAnalyzer.updateReference(frameMat)
    }

    fun latestSignalSnapshot(): FrameSignalSnapshot? = latestSignals
    fun latestOverlapMeasurement(): OverlapMeasurement = latestOverlapMeasurement

    override fun analyze(image: ImageProxy) {
        try {
            val mat = image.toYuvMat() ?: return

            val activeSession = session
            if (activeSession == null || activeSession.isCancelled) {
                mat.release()
                return
            }

            val now = System.currentTimeMillis()
            if (now - lastEmitMs < emitIntervalMs) {
                mat.release()
                return
            }
            lastEmitMs = now

            val overlapMeasurement = overlapAnalyzer.analyze(mat)
            latestOverlapMeasurement = overlapMeasurement
            latestSignals =
                    FrameSignalSnapshot(
                            overlapPct = overlapMeasurement.overlapPct,
                            timestamp = now,
                    )

            emitter.emit("frame", latestSignals!!.toJsObject())
            mat.release()
        } finally {
            image.close()
        }
    }
}
