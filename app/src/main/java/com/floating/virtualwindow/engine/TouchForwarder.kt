package com.floating.virtualwindow.engine

import android.view.MotionEvent
import rikka.shizuku.Shizuku

class TouchForwarder(
    private var windowWidth: Int,
    private var windowHeight: Int,
    private var displayWidth: Int,
    private var displayHeight: Int
) {

    fun updateDimensions(wWidth: Int, wHeight: Int, dWidth: Int, dHeight: Int) {
        this.windowWidth = if (wWidth > 0) wWidth else 1
        this.windowHeight = if (wHeight > 0) wHeight else 1
        this.displayWidth = if (dWidth > 0) dWidth else 1
        this.displayHeight = if (dHeight > 0) dHeight else 1
    }

    fun forwardTouch(event: MotionEvent, displayId: Int) {
        if (!GuestLauncher.isShizukuAvailable()) return

        val scaleX = displayWidth.toFloat() / windowWidth.toFloat()
        val scaleY = displayHeight.toFloat() / windowHeight.toFloat()

        val mappedX = event.x * scaleX
        val mappedY = event.y * scaleY

        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "down"
            MotionEvent.ACTION_UP -> "up"
            MotionEvent.ACTION_MOVE -> "move"
            else -> return
        }

        try {
            val cmd = "input -d $displayId tap $mappedX $mappedY"
            if (action == "down") {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
            }
        } catch (e: Exception) {
            // Ignore background input failures
        }
    }
}
