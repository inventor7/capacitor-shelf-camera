package com.efficy.shelfcamera.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/**
 * Monitors battery level and thermal state to trigger throttling behaviour.
 *
 * When throttled:
 * - Frame analysis drops from 10 Hz to 5 Hz
 * - Incremental stitching is disabled (only final stitchAll at commit)
 * - A `thermalThrottle` warning event is emitted
 */
class ThermalMonitor(
    private val context: Context,
    private val onThrottleChanged: (throttled: Boolean) -> Unit,
) {
    var isThrottled: Boolean = false
        private set

    private var batteryReceiver: BroadcastReceiver? = null
    private var thermalListener: Any? = null // API 29+ typed as PowerManager.OnThermalStatusChangedListener

    fun start() {
        registerBatteryReceiver()
        registerThermalListener()
    }

    fun stop() {
        batteryReceiver?.let { context.unregisterReceiver(it) }
        batteryReceiver = null
        unregisterThermalListener()
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val pct = level * 100 / scale

                val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                // Battery temp is in tenths of °C; throttle above 42°C or below 10% battery
                val shouldThrottle = pct < 10 || temperature > 420
                updateThrottle(shouldThrottle)
            }
        }
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val shouldThrottle = status >= PowerManager.THERMAL_STATUS_SEVERE
                updateThrottle(shouldThrottle)
            }
            pm.addThermalStatusListener(listener)
            thermalListener = listener
        }
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("UNCHECKED_CAST")
            pm.removeThermalStatusListener(
                thermalListener as PowerManager.OnThermalStatusChangedListener
            )
            thermalListener = null
        }
    }

    private fun updateThrottle(shouldThrottle: Boolean) {
        if (shouldThrottle != isThrottled) {
            isThrottled = shouldThrottle
            onThrottleChanged(shouldThrottle)
        }
    }
}
