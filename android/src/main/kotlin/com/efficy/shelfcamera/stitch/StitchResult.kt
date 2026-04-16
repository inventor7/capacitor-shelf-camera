package com.efficy.shelfcamera.stitch

import org.opencv.core.Mat

data class StitchResult(
    val success: Boolean,
    val panorama: Mat?,
    val seamScore: Float,
)
