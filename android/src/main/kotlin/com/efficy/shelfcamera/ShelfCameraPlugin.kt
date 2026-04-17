package com.efficy.shelfcamera

import android.Manifest
import android.util.Log
import android.util.Size
import com.efficy.shelfcamera.sensors.TiltSensor
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
    private var tiltSensor: TiltSensor? = null
    private var thermalMonitor: ThermalMonitor? = null
    private var openCvReady = false

    override fun load() {
        super.load()
        // Explicit System.loadLibrary first so any UnsatisfiedLinkError surfaces
        // verbatim in logcat. OpenCVLoader.StaticHelper silently swallows the
        // cause, which makes missing deps (e.g. libc++_shared.so) look like an
        // opaque DEVICE_UNSUPPORTED. The second call inside initLocal() is a
        // no-op — System.loadLibrary is idempotent.
        openCvReady = try {
            System.loadLibrary("opencv_java4")
            OpenCVLoader.initLocal()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native load failed", e)
            false
        } catch (t: Throwable) {
            Log.e(TAG, "OpenCV init failed", t)
            false
        }
        if (openCvReady) {
            Log.i(TAG, "OpenCV 4.12.0 ready")
        } else {
            Log.e(TAG, "OpenCV not ready — start() will reject with DEVICE_UNSUPPORTED")
        }
    }

    private companion object {
        private const val TAG = "ShelfCamera"
    }

    @PluginMethod
    fun start(call: PluginCall) {
        if (!openCvReady) {
            call.reject("DEVICE_UNSUPPORTED", "OpenCV initialization failed")
            return
        }
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
            call.reject("PERMISSION_DENIED")
        }
    }

    private fun doStart(call: PluginCall) {
        val resolution = call.getString("resolution", "1080p") ?: "1080p"
        val size = when (resolution) {
            "720p" -> Size(1280, 720)
            "2k"   -> Size(2560, 1440)
            else   -> Size(1920, 1080)
        }

        val sensor = TiltSensor(context).also { it.start() }
        tiltSensor = sensor

        val emitter = EventEmitter { name, data -> notifyListeners(name, data) }

        val monitor = ThermalMonitor(context) { throttled ->
            cameraController?.setThrottled(throttled)
            emitter.emit("thermalThrottle", JSObject().apply {
                put("throttled", throttled)
            })
        }
        monitor.start()
        thermalMonitor = monitor

        val controller = CameraController(context, activity, emitter, sensor, bridge)
        cameraController = controller
        controller.start(size, call)
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        thermalMonitor?.stop()
        thermalMonitor = null
        tiltSensor?.stop()
        tiltSensor = null
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
    fun beginPanorama(call: PluginCall) {
        val sessionId = call.getString("sessionId") ?: run {
            call.reject("sessionId is required")
            return
        }
        val mode = call.getString("mode", "sweep") ?: "sweep"
        val thresholds = call.getObject("keyframeThresholds")

        val session = PanoramaSession(
            sessionId = sessionId,
            mode = mode,
            thresholdsJson = thresholds,
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
        val targetCell = call.getObject("targetCell")
        val session = activeSession ?: run {
            call.reject("No active panorama session")
            return
        }
        cameraController?.captureStill(session, targetCell, call)
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
    fun getDeviceTier(call: PluginCall) {
        val tier = DeviceTier.detect(context)
        call.resolve(JSObject().put("tier", tier.name.lowercase()))
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        thermalMonitor?.stop()
        tiltSensor?.stop()
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
