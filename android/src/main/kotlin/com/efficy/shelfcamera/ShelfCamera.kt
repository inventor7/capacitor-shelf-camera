package com.efficy.shelfcamera

import com.getcapacitor.Logger

class ShelfCamera {

    fun echo(value: String): String {
        Logger.info("Echo", value)

        return value
    }
}
