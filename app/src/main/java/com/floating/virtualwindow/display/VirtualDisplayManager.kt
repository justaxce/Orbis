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
        get() = virtualDisplay?.display?.displayId ?: Display.INVALID_DISPLAY

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

        return try {
            virtualDisplay = displayManager.createVirtualDisplay(
                name,
                width,
                height,
                densityDpi,
                surface,
                flags
            )
            displayId
        } catch (e: Exception) {
            e.printStackTrace()
            Display.INVALID_DISPLAY
        }
    }

    fun resize(width: Int, height: Int, densityDpi: Int) {
        if (width > 0 && height > 0) {
            try {
                virtualDisplay?.resize(width, height, densityDpi)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
