package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import kotlin.math.abs
import kotlin.math.max

@SuppressLint("ClickableViewAccessibility")
class EdgeHandleView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onHandleTapped: () -> Unit,
    private val onDockSideChanged: ((String) -> Unit)? = null
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_edge_handle, null)
    val layoutParams: WindowManager.LayoutParams
    private val preferencesManager = PreferencesManager(context)

    private var initialY: Int = 0
    private var initialX: Int = 0
    private var initialTouchY: Float = 0f
    private var initialTouchX: Float = 0f
    private var isDragging: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable {
        view.animate().alpha(0.3f).setDuration(350).start()
    }

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val isRightSide = preferencesManager.dockSide == "RIGHT"
        val gravity = Gravity.TOP or (if (isRightSide) Gravity.END else Gravity.START)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = 0
            this.y = preferencesManager.handleYPosition
        }

        setupTouchListener()
        scheduleDimming()
    }

    private fun scheduleDimming() {
        handler.removeCallbacks(dimRunnable)
        handler.postDelayed(dimRunnable, 3000)
    }

    private fun setupTouchListener() {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(dimRunnable)
                    view.animate().alpha(1.0f).setDuration(150).start()
                    initialY = layoutParams.y
                    initialX = layoutParams.x
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)

                    if (deltaX > 10 || deltaY > 10 || isDragging) {
                        isDragging = true

                        // During 2D dragging across the screen, track finger with TOP-START gravity
                        layoutParams.gravity = Gravity.TOP or Gravity.START
                        val handleWidth = if (view.width > 0) view.width else 100
                        val handleHeight = if (view.height > 0) view.height else 150

                        layoutParams.x = (event.rawX - handleWidth / 2).toInt()
                        layoutParams.y = (event.rawY - handleHeight / 2).toInt()

                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaY = abs(event.rawY - initialTouchY)
                    val deltaX = abs(event.rawX - initialTouchX)

                    if (!isDragging && deltaY < 15 && deltaX < 15) {
                        onHandleTapped()
                    } else if (isDragging) {
                        // Magnetic Edge Snapping: check if dropped on Left or Right half of screen
                        val displayMetrics = context.resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        val newDockSide = if (event.rawX < screenWidth / 2) "LEFT" else "RIGHT"
                        preferencesManager.dockSide = newDockSide

                        val handleHeight = if (view.height > 0) view.height else 150
                        val clampedY = (event.rawY - handleHeight / 2).toInt().coerceIn(80, screenHeight - 180)
                        preferencesManager.handleYPosition = clampedY

                        layoutParams.gravity = Gravity.TOP or (if (newDockSide == "RIGHT") Gravity.END else Gravity.START)
                        layoutParams.x = 0
                        layoutParams.y = clampedY

                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        onDockSideChanged?.invoke(newDockSide)
                    }
                    scheduleDimming()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleDimming()
                    false
                }
                else -> false
            }
        }
    }

    fun updateDockSide(dockSide: String) {
        val isRightSide = dockSide == "RIGHT"
        layoutParams.gravity = Gravity.TOP or (if (isRightSide) Gravity.END else Gravity.START)
        layoutParams.x = 0
        layoutParams.y = preferencesManager.handleYPosition
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun show() {
        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
                scheduleDimming()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        handler.removeCallbacks(dimRunnable)
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleOrientationChanged() {
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val handleHeight = if (view.height > 0) view.height else 150
        val clampedY = layoutParams.y.coerceIn(60, max(60, screenHeight - handleHeight - 60))
        layoutParams.y = clampedY
        preferencesManager.handleYPosition = clampedY
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
