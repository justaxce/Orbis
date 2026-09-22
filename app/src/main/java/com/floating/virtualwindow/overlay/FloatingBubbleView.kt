package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.floating.virtualwindow.R
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onBubbleTapped: () -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_floating_bubble, null)
    val layoutParams: WindowManager.LayoutParams
    private val ivBubbleIcon: ImageView = view.findViewById(R.id.ivBubbleIcon)

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 400
        }

        setupTouchListener()
    }

    private fun setupTouchListener() {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10 || isDragging) {
                        isDragging = true
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (!isDragging && deltaX < 15 && deltaY < 15) {
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun setIcon(icon: Drawable?) {
        if (icon != null) {
            ivBubbleIcon.setImageDrawable(icon)
        } else {
            ivBubbleIcon.setImageResource(R.mipmap.ic_launcher)
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
