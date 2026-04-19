package com.efficy.shelfcamera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.efficy.shelfcamera.util.EventEmitter
import com.getcapacitor.JSObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps CameraX [VideoCapture] to record shelf sweeps as MP4 clips.
 *
 * Deliberately isolated from [CameraController] so the existing Preview +
 * ImageAnalysis use-cases keep running during recording (coaching signals
 * remain live). CameraX on API 30+ supports all four use-cases concurrently
 * on most mid/high-tier devices.
 */
class VideoRecorder(
        private val context: Context,
        private val activity: FragmentActivity,
        private val emitter: EventEmitter,
) {
    private val TAG = "VideoRecorder"

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private val isRecording = AtomicBoolean(false)

    /** Output file for the current session recording. */
    private var outputFile: File? = null
    private var autoStopTimer: java.util.Timer? = null

    /** Bind the VideoCapture use-case to the already-running [provider]. */
    fun bind(provider: ProcessCameraProvider, activity: FragmentActivity) {
        val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture!!,
            )
            Log.i(TAG, "VideoCapture use-case bound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind VideoCapture", e)
            videoCapture = null
        }
    }

    fun unbind(provider: ProcessCameraProvider) {
        videoCapture?.let { provider.unbind(it) }
        videoCapture = null
    }

    /**
     * Begin recording. Resolves immediately; use [stopAndGetUri] to finalise.
     * Auto-stops after [maxDurationMs].
     */
    fun start(
            sessionId: String,
            maxDurationMs: Long,
            onAutoStopped: (videoUri: String) -> Unit,
            onError: (msg: String) -> Unit,
    ) {
        val vc = videoCapture ?: run {
            onError("VideoCapture not bound — call bind() first")
            return
        }
        if (!isRecording.compareAndSet(false, true)) {
            onError("Already recording")
            return
        }

        val dir = File(context.filesDir, "shelfcam/sessions/$sessionId").also { it.mkdirs() }
        val file = File(dir, "video.mp4").also { if (it.exists()) it.delete() }
        outputFile = file

        val opts = FileOutputOptions.Builder(file).build()

        emitter.emit("videoProgress", JSObject().apply {
            put("sessionId", sessionId)
            put("extractedFrames", 0)
            put("acceptedFrames", 0)
            put("phase", "recording")
        })

        val recording = vc.output
                .prepareRecording(context, opts)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Finalize -> {
                            isRecording.set(false)
                            autoStopTimer?.cancel()
                            if (event.hasError()) {
                                Log.e(TAG, "Recording finalized with error: ${event.cause}")
                                onError("Recording error: ${event.cause?.message}")
                            } else {
                                Log.i(TAG, "Recording saved to ${file.absolutePath}")
                                onAutoStopped("file://${file.absolutePath}")
                            }
                        }
                        else -> {} // Start / Status events — no-op
                    }
                }
        activeRecording = recording

        // Auto-stop safety timer
        autoStopTimer = java.util.Timer().also { timer ->
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    if (isRecording.get()) {
                        Log.i(TAG, "Auto-stopping recording after ${maxDurationMs}ms")
                        recording.stop()
                    }
                }
            }, maxDurationMs)
        }
    }

    /** Manual early stop. The [onAutoStopped] callback set in [start] fires with the URI. */
    fun stop() {
        if (isRecording.get()) {
            activeRecording?.stop()
        }
    }

    val isActive: Boolean get() = isRecording.get()
}
