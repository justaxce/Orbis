package com.floating.virtualwindow.engine

import android.view.MotionEvent
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/**
 * Forwards touch and navigation events from the Orbis floating window to a target Virtual Display
 * using privileged Shizuku shell execution.
 *
 * Supported interactions:
 * - Tap (ACTION_DOWN -> ACTION_UP with small movement & quick release)
 * - Long Press (ACTION_DOWN -> ACTION_UP held >= 500ms in place)
 * - Drag / Swipe / Scroll (ACTION_DOWN -> ACTION_MOVE -> ACTION_UP with delta >= TOUCH_SLOP)
 * - Back button injection (sendBackKey)
 *
 * Architecture & Limitations:
 * Stock Android privileged shell utilizes the `input` CLI binary (`input -d <displayId> ...`).
 * The `input` binary natively supports single-touch pointer operations (tap, swipe, keyevent).
 * Multi-touch gestures (e.g. pinch-to-zoom) require direct kernel /dev/uinput virtual device
 * creation, which requires full Linux root access and is not supported by standard Android CLI `input`.
 */
class TouchForwarder(
    private var windowWidth: Int,
    private var windowHeight: Int,
    private var displayWidth: Int,
    private var displayHeight: Int
) {

    private val inputExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TouchForwarder-Worker").apply { isDaemon = true }
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTimeMs = 0L
    private var isDragging = false

    companion object {
        private const val TOUCH_SLOP = 16f
        private const val LONG_PRESS_TIMEOUT_MS = 500L
    }

    fun updateDimensions(wWidth: Int, wHeight: Int, dWidth: Int, dHeight: Int) {
        this.windowWidth = if (wWidth > 0) wWidth else 1
        this.windowHeight = if (wHeight > 0) wHeight else 1
        this.displayWidth = if (dWidth > 0) dWidth else 1
        this.displayHeight = if (dHeight > 0) dHeight else 1
    }

    fun forwardTouch(event: MotionEvent, displayId: Int) {
        if (displayId <= 0 || !GuestLauncher.isShizukuAvailable()) return

        val scaleX = displayWidth.toFloat() / windowWidth.toFloat()
        val scaleY = displayHeight.toFloat() / windowHeight.toFloat()

        val mappedX = (event.x * scaleX).coerceIn(0f, displayWidth.toFloat())
        val mappedY = (event.y * scaleY).coerceIn(0f, displayHeight.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = mappedX
                downY = mappedY
                lastX = mappedX
                lastY = mappedY
                downTimeMs = System.currentTimeMillis()
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(mappedX - downX, mappedY - downY)
                if (distance > TOUCH_SLOP) {
                    isDragging = true
                }
                lastX = mappedX
                lastY = mappedY
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - downTimeMs
                val distance = hypot(mappedX - downX, mappedY - downY)

                if (!isDragging && distance < TOUCH_SLOP) {
                    if (duration >= LONG_PRESS_TIMEOUT_MS) {
                        // Long press simulated via stationary swipe
                        executeShellCommand("input -d $displayId swipe ${downX.toInt()} ${downY.toInt()} ${downX.toInt()} ${downY.toInt()} 800")
                    } else {
                        // Single Tap
                        executeShellCommand("input -d $displayId tap ${downX.toInt()} ${downY.toInt()}")
                    }
                } else {
                    // Drag / Swipe / Scroll
                    val swipeDuration = duration.coerceIn(80, 500)
                    executeShellCommand("input -d $displayId swipe ${downX.toInt()} ${downY.toInt()} ${mappedX.toInt()} ${mappedY.toInt()} $swipeDuration")
                }
                isDragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
    }

    fun sendBackKey(displayId: Int) {
        if (displayId <= 0 || !GuestLauncher.isShizukuAvailable()) return
        executeShellCommand("input -d $displayId keyevent 4")
    }

    private fun executeShellCommand(cmd: String) {
        inputExecutor.execute {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }

                val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                process?.waitFor(1500, TimeUnit.MILLISECONDS)
                process?.destroy()
            } catch (e: Exception) {
                // Ignore input failures in background
            }
        }
    }
}
