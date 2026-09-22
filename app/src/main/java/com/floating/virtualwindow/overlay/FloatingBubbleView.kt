package com.floating.virtualwindow.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.floating.virtualwindow.R
import kotlin.math.abs
import kotlin.math.max

@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val dismissTargetView: BubbleDismissTargetView,
    private val onBubbleTapped: () -> Unit,
    private val onBubbleDismissed: () -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_floating_bubble, null)
    val layoutParams: WindowManager.LayoutParams
    private val ivBubbleIcon: ImageView = view.findViewById(R.id.ivBubbleIcon)

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    private var isHoveringDismiss: Boolean = false
    private var hasVibratedForHover: Boolean = false

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
            x = 24
            y = 400
        }

        setupTouchListener()
    }

    private fun setupTouchListener() {
        view.setOnTouchListener { _, event ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isHoveringDismiss = false
                    hasVibratedForHover = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 10 || abs(deltaY) > 10 || isDragging) {
                        if (!isDragging) {
                            isDragging = true
                            dismissTargetView.show()
                        }

                        val currentlyHovered = dismissTargetView.checkHover(event.rawX, event.rawY)
                        isHoveringDismiss = currentlyHovered

                        if (currentlyHovered) {
                            if (!hasVibratedForHover) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                hasVibratedForHover = true
                            }
                            // Magnetically snap bubble directly over the dismiss cross target
                            val (targetCenterX, targetCenterY) = dismissTargetView.getTargetCenter()
                            val bWidth = if (view.width > 0) view.width else (displayMetrics.density * 54).toInt()
                            val bHeight = if (view.height > 0) view.height else (displayMetrics.density * 54).toInt()
                            layoutParams.x = (targetCenterX - bWidth / 2f).toInt()
                            layoutParams.y = (targetCenterY - bHeight / 2f).toInt()
                        } else {
                            hasVibratedForHover = false
                            layoutParams.x = initialX + deltaX
                            layoutParams.y = initialY + deltaY
                        }

                        updateLayout()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val wasDragging = isDragging
                    val wasHovering = isHoveringDismiss

                    dismissTargetView.hide()
                    isDragging = false
                    isHoveringDismiss = false

                    if (wasHovering) {
                        // User dropped bubble onto the cross! Dismiss app completely!
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        animateDismissAndClose()
                    } else if (wasDragging) {
                        // User dropped bubble elsewhere: Magnetically snap to closest edge (left or right)
                        val bWidth = if (view.width > 0) view.width else (displayMetrics.density * 54).toInt()
                        val bHeight = if (view.height > 0) view.height else (displayMetrics.density * 54).toInt()
                        val edgeMargin = (displayMetrics.density * 16).toInt()

                        val targetX = if (event.rawX > screenWidth / 2f) {
                            screenWidth - bWidth - edgeMargin
                        } else {
                            edgeMargin
                        }
                        val targetY = layoutParams.y.coerceIn(edgeMargin, screenHeight - bHeight - edgeMargin)

                        animateBubbleSnap(targetX, targetY)
                    } else {
                        // Quick tap: Restore floating window!
                        val deltaX = abs(event.rawX - initialTouchX)
                        val deltaY = abs(event.rawY - initialTouchY)
                        if (deltaX < 15 && deltaY < 15) {
                            onBubbleTapped()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun animateBubbleSnap(targetX: Int, targetY: Int) {
        val startX = layoutParams.x
        val startY = layoutParams.y

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                layoutParams.x = (startX + (targetX - startX) * fraction).toInt()
                layoutParams.y = (startY + (targetY - startY) * fraction).toInt()
                updateLayout()
            }
        }
        animator.start()
    }

    private fun animateDismissAndClose() {
        view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(160)
            .withEndAction {
                hide()
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                onBubbleDismissed()
            }
            .start()
    }

    private fun updateLayout() {
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                // Ignored
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
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f

        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        dismissTargetView.hide()
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
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = (displayMetrics.density * 56).toInt()
        val edgeMargin = (displayMetrics.density * 16).toInt()

        layoutParams.x = layoutParams.x.coerceIn(edgeMargin, max(edgeMargin, screenWidth - bubbleSize - edgeMargin))
        layoutParams.y = layoutParams.y.coerceIn(edgeMargin, max(edgeMargin, screenHeight - bubbleSize - edgeMargin))
        updateLayout()
    }
}
