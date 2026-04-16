package com.efficy.shelfcamera.util

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin

/**
 * Thin wrapper around [Plugin.notifyListeners] with throttling support.
 * The plugin reference is nullable so EventEmitter can be instantiated
 * before the plugin is fully wired (e.g. in unit tests).
 */
class EventEmitter(private val plugin: Plugin?) {

    fun emit(eventName: String, data: JSObject) {
        plugin?.notifyListeners(eventName, data) ?: Unit
    }
}
