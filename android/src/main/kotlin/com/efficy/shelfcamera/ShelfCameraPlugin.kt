package com.efficy.shelfcamera

import android.Manifest
import android.util.Log
import android.util.Size
import com.efficy.shelfcamera.keyframe.KeyframeStore
import com.efficy.shelfcamera.util.DeviceTier
import com.efficy.shelfcamera.util.EventEmitter
import com.efficy.shelfcamera.util.ThermalMonitor
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import org.opencv.android.OpenCVLoader

@CapacitorPlugin(
    name = "ShelfCamera",
    permissions = [
        Permission(
            strings = [Manifest.permission.CAMERA],
            alias = "camera",
        )
    ]
)
class ShelfCameraPlugin : com.getcapacitor.Plugin() {

    private var cameraController: CameraController? = null
    private var activeSession: PanoramaSession? = null
    private var thermalMonitor: ThermalMonitor? = null
    private var isOpenCvReady = false


    private companion object {
        private const val TAG = "ShelfCamera"
    }

    @PluginMethod
    fun start(call: PluginCall) {

        if (getPermissionState("camera") != PermissionState.GRANTED) {
            requestPermissionForAlias("camera", call, "cameraPermCallback")
            return
        }
        doStart(call)
    }

    @PermissionCallback
    private fun cameraPermCallback(call: PluginCall) {
        if (getPermissionState("camera") == PermissionState.GRANTED) {
            doStart(call)
        } else {
            notifyListeners("error", JSObject().apply {
                put("code", "PERMISSION_DENIED")
                put("message", "Camera permission denied")
            })
            call.reject("Camera permission denied", "PERMISSION_DENIED")
        }
    }

    private fun doStart(call: PluginCall) {
        if (!ensureOpenCvReady()) {
            notifyListeners("error", JSObject().apply {
                put("code", "DEVICE_UNSUPPORTED")
                put("message", "OpenCV failed to initialize")
            })
            call.reject("OpenCV failed to initialize", "DEVICE_UNSUPPORTED")
            return
        }

        val resolution = call.getString("resolution", "1080p") ?: "1080p"
        val size = when (resolution) {
            "720p" -> Size(960, 720)
            "2k"   -> Size(2048, 1536)
            else   -> Size(1440, 1080)
        }

        val emitter = EventEmitter { name, data -> notifyListeners(name, data) }

        val monitor = ThermalMonitor(context) { throttled ->
            cameraController?.setThrottled(throttled)
            emitter.emit("thermalThrottle", JSObject().apply {
                put("throttled", throttled)
            })
        }
        monitor.start()
        thermalMonitor = monitor

        val controller = CameraController(context, activity, emitter, bridge)
        cameraController = controller
        controller.start(size, call)
    }

    private fun ensureOpenCvReady(): Boolean {
        if (isOpenCvReady) return true

        return try {
            isOpenCvReady = OpenCVLoader.initLocal()
            if (!isOpenCvReady) {
                Log.e(TAG, "OpenCV initLocal() returned false")
            }
            isOpenCvReady
        } catch (error: Throwable) {
            Log.e(TAG, "OpenCV initialization failed", error)
            false
        }
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        thermalMonitor?.stop()
        thermalMonitor = null
        activeSession?.cancel()
        cameraController?.stop()
        cameraController = null
        activeSession = null
        call.resolve()
    }

    @PluginMethod
    fun setPreviewVisible(call: PluginCall) {
        val visible = call.getBoolean("visible", true) ?: true
        cameraController?.setPreviewVisible(visible)
        call.resolve()
    }

    @PluginMethod
    fun setPreviewFrame(call: PluginCall) {
        val x = call.getDouble("x") ?: run {
            call.reject("x is required")
            return
        }
        val y = call.getDouble("y") ?: run {
            call.reject("y is required")
            return
        }
        val width = call.getDouble("width") ?: run {
            call.reject("width is required")
            return
        }
        val height = call.getDouble("height") ?: run {
            call.reject("height is required")
            return
        }
        cameraController?.setPreviewFrame(x, y, width, height)
        call.resolve()
    }

    @PluginMethod
    fun setCaptureCropRegion(call: PluginCall) {
        if (call.getBoolean("enabled", true) == false) {
            cameraController?.clearCaptureCropRegion()
            call.resolve()
            return
        }

        val x = call.getDouble("x") ?: run {
            call.reject("x is required")
            return
        }
        val y = call.getDouble("y") ?: run {
            call.reject("y is required")
            return
        }
        val width = call.getDouble("width") ?: run {
            call.reject("width is required")
            return
        }
        val height = call.getDouble("height") ?: run {
            call.reject("height is required")
            return
        }
        cameraController?.setCaptureCropRegion(x, y, width, height)
        call.resolve()
    }

    @PluginMethod
    fun beginPanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val mode = call.getString("mode", "manual") ?: "manual"
        if (mode != "manual") {
            call.reject("Only manual panorama mode is supported.")
            return
        }

        val session = PanoramaSession(
            sessionId = sessionId,
            expectedCells = call.getInt("expectedCells"),
            plugin = this,
            context = context,
        )
        activeSession = session
        cameraController?.setActiveSession(session)
        call.resolve()
    }

    @PluginMethod
    fun capturePhoto(call: PluginCall) {
        call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession ?: run {
            call.reject("No active panorama session")
            return
        }
        cameraController?.captureManualFrame(session, call)
    }

    @PluginMethod
    fun commitPanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession?.takeIf { it.sessionId == sessionId } ?: run {
            call.reject("No active session: $sessionId")
            return
        }
        session.commit(call) { event ->
            activeSession = null
            cameraController?.setActiveSession(null)
            notifyListeners("panoramaReady", event)
        }
    }

    @PluginMethod
    fun cancelPanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession?.takeIf { it.sessionId == sessionId }
        if (session != null) {
            session.cancel()
            notifyListeners("error", JSObject().apply {
                put("code", "ABORTED")
                put("message", "Panorama session $sessionId was cancelled")
            })
        }
        activeSession = null
        cameraController?.setActiveSession(null)
        call.resolve()
    }

    @PluginMethod
    fun deletePanoramaSession(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession?.takeIf { it.sessionId == sessionId }
        if (session != null) {
            session.cancel()
            activeSession = null
            cameraController?.setActiveSession(null)
        } else {
            KeyframeStore(context).deleteSession(sessionId)
        }
        call.resolve()
    }

    @PluginMethod
    fun pausePanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession?.takeIf { it.sessionId == sessionId } ?: run {
            call.reject("No active session: $sessionId")
            return
        }
        cameraController?.setActiveSession(null)
        call.resolve(JSObject().put("sessionId", session.sessionId))
    }

    @PluginMethod
    fun resumePanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val session = activeSession?.takeIf { it.sessionId == sessionId } ?: run {
            call.reject("No active session: $sessionId")
            return
        }
        cameraController?.setActiveSession(session)
        call.resolve(JSObject().put("sessionId", session.sessionId))
    }

    @PluginMethod
    fun getDeviceTier(call: PluginCall) {
        val tier = DeviceTier.detect(context)
        call.resolve(JSObject().put("tier", tier.name.lowercase()))
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        thermalMonitor?.stop()
        activeSession?.let {
            it.cancel()
            notifyListeners("error", JSObject().apply {
                put("code", "ABORTED")
                put("message", "Plugin destroyed while session ${it.sessionId} was active")
            })
        }
        cameraController?.stop()
    }
}
