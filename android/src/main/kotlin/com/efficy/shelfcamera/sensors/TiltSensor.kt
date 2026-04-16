package com.efficy.shelfcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.acos
import kotlin.math.min

/**
 * Subscribes to TYPE_ROTATION_VECTOR and exposes the current tilt as
 * degrees deviation from upright.
 */
class TiltSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var currentTiltDeg: Float = 0f
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

        // Device-up vector in world frame = third column of rotation matrix
        val deviceUpX = rotationMatrix[2]
        val deviceUpY = rotationMatrix[5]
        val deviceUpZ = rotationMatrix[8]

        // Dot product with world up (0, 1, 0)
        val dot = deviceUpY.coerceIn(-1f, 1f)
        currentTiltDeg = Math.toDegrees(acos(dot.toDouble())).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
