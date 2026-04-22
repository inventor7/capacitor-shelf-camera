package com.efficy.shelfcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.acos
import kotlin.math.asin

/**
 * Subscribes to TYPE_ROTATION_VECTOR and exposes the current tilt as
 * degrees deviation from upright.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var currentTiltDeg: Float = 0f
        private set

    var currentRollDeg: Float = 0f
        private set

    var levelOffsetX: Float = 0f
        private set

    var levelOffsetY: Float = 0f
        private set

    fun start() {
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Android's rotation matrix maps device → world, where world axes are
        // (East, North, Sky). "World up" is the Z axis = (0, 0, 1).
        //
        // For an upright phone held in portrait orientation, the device Y axis
        // (screen-up) should point toward the sky. So "tilt from upright" is the
        // angle between device-Y (in world frame) and world-Z.
        //
        // Device Y expressed in world frame = 2nd column of R = (R[0,1], R[1,1], R[2,1])
        //                                                     = (rotationMatrix[1,4,7])
        // Dot with (0,0,1) = rotationMatrix[7].
        val upX = rotationMatrix[6].coerceIn(-1f, 1f)
        val upY = rotationMatrix[7].coerceIn(-1f, 1f)
        val upZ = rotationMatrix[8].coerceIn(-1f, 1f)

        levelOffsetX = upX
        levelOffsetY = upZ
        currentTiltDeg = Math.toDegrees(acos(upY.toDouble())).toFloat()
        currentRollDeg = Math.toDegrees(asin(upX.toDouble())).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
