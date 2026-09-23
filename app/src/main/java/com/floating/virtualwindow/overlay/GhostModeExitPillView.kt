package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.floating.virtualwindow.R
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class GhostModeExitPillView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onExitClicked: () -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_ghost_exit_pill, null)
    private val layoutParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (45 * density).toInt()
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy
                    if (view.parent != null) {
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val totalDx = abs(event.rawX - initialTouchX)
                    val totalDy = abs(event.rawY - initialTouchY)
                    if (totalDx < 15 && totalDy < 15) {
                        onExitClicked()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun show() {
        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
