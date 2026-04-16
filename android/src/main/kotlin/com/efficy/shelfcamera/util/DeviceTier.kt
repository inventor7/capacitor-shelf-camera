package com.efficy.shelfcamera.util

import android.app.ActivityManager
import android.content.Context

enum class Tier { low, mid, high }

/**
 * Classifies the device into a performance tier used to adapt
 * analysis frequency and incremental stitching behaviour.
 *
 * - high: ≥ 8 cores AND ≥ 6 GB RAM
 * - low:  ≤ 4 cores OR < 3 GB RAM
 * - mid:  everything else
 */
object DeviceTier {

    fun detect(context: Context): Tier {
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalGb = info.totalMem / (1024.0 * 1024 * 1024)

        return when {
            cores >= 8 && totalGb >= 6.0 -> Tier.high
            cores <= 4 || totalGb < 3.0  -> Tier.low
            else                          -> Tier.mid
        }
    }
}
