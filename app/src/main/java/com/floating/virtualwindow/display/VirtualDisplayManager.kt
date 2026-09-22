package com.floating.virtualwindow.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface

class VirtualDisplayManager(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    var virtualDisplay: VirtualDisplay? = null
        private set

    val displayId: Int
        get() = virtualDisplay?.display?.displayId ?: Display.DEFAULT_DISPLAY

    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface
    ): Int {
        release()
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        virtualDisplay = displayManager.createVirtualDisplay(
            name,
            width,
            height,
            densityDpi,
            surface,
            flags
        )
        return displayId
    }

    fun resize(width: Int, height: Int, densityDpi: Int) {
        virtualDisplay?.resize(width, height, densityDpi)
    }

    fun release() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        virtualDisplay = null
    }
}
