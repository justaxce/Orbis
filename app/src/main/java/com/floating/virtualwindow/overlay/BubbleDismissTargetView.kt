package com.floating.virtualwindow.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.floating.virtualwindow.R

class BubbleDismissTargetView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.view_bubble_dismiss_target, null)
    private val layoutParams: WindowManager.LayoutParams

    private val flDismissTarget: FrameLayout = view.findViewById(R.id.flDismissTarget)
    private val tvDismissLabel: TextView = view.findViewById(R.id.tvDismissLabel)

    private var isHovered: Boolean = false
    private var isVisible: Boolean = false

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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (context.resources.displayMetrics.density * 44).toInt()
        }

        view.alpha = 0f
        view.scaleX = 0.5f
        view.scaleY = 0.5f
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        setHovered(false)

        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        view.animate().cancel()
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        setHovered(false)

        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(180)
            .withEndAction {
                if (!isVisible && view.parent != null) {
                    try {
                        windowManager.removeView(view)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            .start()
    }

    fun setHovered(hovered: Boolean) {
        if (isHovered == hovered) return
        isHovered = hovered

        if (hovered) {
            flDismissTarget.setBackgroundResource(R.drawable.bg_bubble_dismiss_target_active)
            tvDismissLabel.text = "Release to close"
            tvDismissLabel.setTextColor(0xFFE74C3C.toInt())

            flDismissTarget.animate().cancel()
            flDismissTarget.animate()
                .scaleX(1.25f)
                .scaleY(1.25f)
                .setDuration(160)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        } else {
            flDismissTarget.setBackgroundResource(R.drawable.bg_bubble_dismiss_target)
            tvDismissLabel.text = "Drag here to close"
            tvDismissLabel.setTextColor(0xB3FFFFFF.toInt())

            flDismissTarget.animate().cancel()
            flDismissTarget.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .start()
        }
    }

    /**
     * Checks if (rawX, rawY) is within the hover / snap radius of the dismiss target.
     */
    fun checkHover(rawX: Float, rawY: Float): Boolean {
        if (!isVisible || view.parent == null) return false

        val location = IntArray(2)
        flDismissTarget.getLocationOnScreen(location)
        val targetCenterX = location[0] + flDismissTarget.width / 2f
        val targetCenterY = location[1] + flDismissTarget.height / 2f

        val dx = rawX - targetCenterX
        val dy = rawY - targetCenterY
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        val snapRadius = context.resources.displayMetrics.density * 85f // 85dp radius
        val hovered = distance <= snapRadius
        setHovered(hovered)
        return hovered
    }

    fun getTargetCenter(): Pair<Float, Float> {
        val location = IntArray(2)
        flDismissTarget.getLocationOnScreen(location)
        val targetCenterX = location[0] + flDismissTarget.width / 2f
        val targetCenterY = location[1] + flDismissTarget.height / 2f
        return Pair(targetCenterX, targetCenterY)
    }
}
