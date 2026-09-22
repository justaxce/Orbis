package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.display.VirtualDisplayManager
import com.floating.virtualwindow.engine.GuestLauncher
import com.floating.virtualwindow.engine.TouchForwarder
import com.floating.virtualwindow.tools.FloatingBrowserView
import com.floating.virtualwindow.tools.FloatingCalculatorView
import com.floating.virtualwindow.ui.WirelessGuideDialog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ClickableViewAccessibility")
class FloatingWindowView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onMinimizeRequested: () -> Unit,
    private val onCloseRequested: () -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_floating_window, null)
    val layoutParams: WindowManager.LayoutParams
    private val preferencesManager = PreferencesManager(context)

    private val ivHeaderIcon: ImageView = view.findViewById(R.id.ivHeaderIcon)
    private val tvHeaderTitle: TextView = view.findViewById(R.id.tvHeaderTitle)
    private val windowHeader: LinearLayout = view.findViewById(R.id.windowHeader)
    private val btnRotateRatio: ImageButton = view.findViewById(R.id.btnRotateRatio)
    private val btnMinimize: ImageButton = view.findViewById(R.id.btnMinimize)
    private val btnMaximize: ImageButton = view.findViewById(R.id.btnMaximize)
    private val btnClose: ImageButton = view.findViewById(R.id.btnClose)
    private val contentContainer: FrameLayout = view.findViewById(R.id.windowContentContainer)
    private val flResizeBR: FrameLayout = view.findViewById(R.id.flResizeBR)
    private val flResizeBL: FrameLayout = view.findViewById(R.id.flResizeBL)
    private val tvResizeBadge: TextView = view.findViewById(R.id.tvResizeBadge)

    var isLandscapeRatio: Boolean = false
    val currentAspectRatio: Float get() = if (isLandscapeRatio) ASPECT_RATIO_16_9 else ASPECT_RATIO_9_16

    private val virtualDisplayManager = VirtualDisplayManager(context)
    private val touchForwarder = TouchForwarder(750, 1100, 750, 1100)

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private var initialWidth: Int = 0
    private var initialHeight: Int = 0

    private var isMaximized: Boolean = false
    private var savedWidth: Int = 0
    private var savedHeight: Int = 0
    private var savedX: Int = 0
    private var savedY: Int = 0

    private var isKeyboardFocusEnabled: Boolean = false

    private var currentSurfaceView: SurfaceView? = null
    private var currentBrowserView: FloatingBrowserView? = null
    private var currentCalculatorView: FloatingCalculatorView? = null

    val currentHeaderIcon: Drawable? get() = ivHeaderIcon.drawable
    val currentTitle: String get() = tvHeaderTitle.text.toString()

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = screenWidth > screenHeight

        val safeInitialHeight = if (isLandscape) {
            (screenHeight * 0.84).toInt()
        } else {
            val preferredWidth = preferencesManager.windowWidth.coerceAtLeast(320)
            val safeWidth = min(preferredWidth, (screenWidth * 0.90).toInt())
            (safeWidth / ASPECT_RATIO_9_16).toInt().coerceAtMost((screenHeight * 0.80).toInt())
        }
        val finalInitialWidth = (safeInitialHeight * ASPECT_RATIO_9_16).toInt()
        val safeInitialX = (screenWidth - finalInitialWidth) / 2
        val safeInitialY = if (isLandscape) {
            (screenHeight - safeInitialHeight) / 2
        } else {
            (screenHeight * 0.08).toInt()
        }

        // Important: Use FLAG_NOT_FOCUSABLE by default so underlying apps (WhatsApp, Instagram, etc.)
        // retain 100% input and soft keyboard (IME) capability without interference.
        // Omit FLAG_LAYOUT_NO_LIMITS so window bounds are properly constrained to the screen.
        layoutParams = WindowManager.LayoutParams(
            finalInitialWidth,
            safeInitialHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = safeInitialX
            y = safeInitialY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        setupDragHandle()
        setupResizeHandle()
        setupControls()
        setupOutsideTouchListener()
    }

    private fun setupOutsideTouchListener() {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                if (preferencesManager.autoMinimizeOnOutsideTap) {
                    onMinimizeRequested()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun setupDragHandle() {
        windowHeader.setOnTouchListener { _, event ->
            val displayMetrics = context.resources.displayMetrics
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = max(0, displayMetrics.widthPixels - layoutParams.width)
                    val maxY = max(0, displayMetrics.heightPixels - layoutParams.height)

                    layoutParams.x = (initialX + (event.rawX - initialTouchX).toInt()).coerceIn(0, maxX)
                    layoutParams.y = (initialY + (event.rawY - initialTouchY).toInt()).coerceIn(0, maxY)
                    updateLayout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupResizeHandle() {
        // Bottom-Right Corner Crop Handle (40dp hitbox)
        flResizeBR.setOnTouchListener { _, event ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isLandscape = screenWidth > screenHeight

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    showResizeBadge(initialWidth, initialHeight)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // In landscape ratio (16:9), dx is the dominant vector
                    val deltaW = if (isLandscapeRatio) {
                        dx
                    } else {
                        if (isLandscape) {
                            if (abs(dx) > abs(dy) * 0.5f) dx else dy * currentAspectRatio
                        } else {
                            (dx + dy * currentAspectRatio) / 2.0f
                        }
                    }

                    val minDimension = min(screenWidth, screenHeight)
                    val minW = if (isLandscapeRatio) (minDimension * 0.45).toInt().coerceIn(320, 560)
                               else (minDimension * 0.28).toInt().coerceIn(200, 320)
                    val maxAvailableW = screenWidth - layoutParams.x
                    val maxAvailableH = screenHeight - layoutParams.y
                    val maxBoundW = min((screenWidth * 0.95).toInt(), maxAvailableW)
                    val maxBoundH = min((screenHeight * 0.92).toInt(), maxAvailableH)
                    val clampedMaxW = max(minW, min(maxBoundW, (maxBoundH * currentAspectRatio).toInt()))

                    val newWidth = (initialWidth + deltaW.toInt()).coerceIn(minW, clampedMaxW)
                    val newHeight = (newWidth / currentAspectRatio).toInt()

                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    preferencesManager.windowWidth = newWidth
                    preferencesManager.windowHeight = newHeight

                    virtualDisplayManager.resize(newWidth, newHeight, 320)
                    touchForwarder.updateDimensions(newWidth, newHeight, newWidth, newHeight)
                    updateLayout()

                    updateResizeBadge(newWidth, newHeight)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hideResizeBadge()
                    true
                }
                else -> false
            }
        }

        // Bottom-Left Corner Crop Handle (40dp hitbox)
        flResizeBL.setOnTouchListener { _, event ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isLandscape = screenWidth > screenHeight

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    showResizeBadge(initialWidth, initialHeight)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // Dragging left (-dx) expands the window horizontally
                    val deltaW = if (isLandscapeRatio) {
                        -dx
                    } else {
                        if (isLandscape) {
                            if (abs(dx) > abs(dy) * 0.5f) -dx else dy * currentAspectRatio
                        } else {
                            (-dx + dy * currentAspectRatio) / 2.0f
                        }
                    }

                    val minDimension = min(screenWidth, screenHeight)
                    val minW = if (isLandscapeRatio) (minDimension * 0.45).toInt().coerceIn(320, 560)
                               else (minDimension * 0.28).toInt().coerceIn(200, 320)
                    val maxRight = initialX + initialWidth
                    val maxAvailableW = maxRight
                    val maxAvailableH = screenHeight - layoutParams.y
                    val maxBoundW = min((screenWidth * 0.95).toInt(), maxAvailableW)
                    val maxBoundH = min((screenHeight * 0.92).toInt(), maxAvailableH)
                    val clampedMaxW = max(minW, min(maxBoundW, (maxBoundH * currentAspectRatio).toInt()))

                    val targetW = (initialWidth + deltaW.toInt()).coerceIn(minW, clampedMaxW)
                    val newX = (maxRight - targetW).coerceIn(0, maxRight - minW)
                    val actualWidth = maxRight - newX
                    val newHeight = (actualWidth / currentAspectRatio).toInt()

                    layoutParams.x = newX
                    layoutParams.width = actualWidth
                    layoutParams.height = newHeight
                    preferencesManager.windowWidth = actualWidth
                    preferencesManager.windowHeight = newHeight

                    virtualDisplayManager.resize(actualWidth, newHeight, 320)
                    touchForwarder.updateDimensions(actualWidth, newHeight, actualWidth, newHeight)
                    updateLayout()

                    updateResizeBadge(actualWidth, newHeight)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hideResizeBadge()
                    true
                }
                else -> false
            }
        }
    }

    private fun showResizeBadge(width: Int, height: Int) {
        val tag = if (isLandscapeRatio) "🎮 16:9" else "📐 9:16"
        tvResizeBadge.text = "$tag • ${width} × ${height}"
        tvResizeBadge.alpha = 0f
        tvResizeBadge.visibility = View.VISIBLE
        tvResizeBadge.animate().alpha(1f).setDuration(120).start()
    }

    private fun updateResizeBadge(width: Int, height: Int) {
        val tag = if (isLandscapeRatio) "🎮 16:9" else "📐 9:16"
        tvResizeBadge.text = "$tag • ${width} × ${height}"
    }

    private fun hideResizeBadge() {
        tvResizeBadge.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                tvResizeBadge.visibility = View.GONE
            }
            .start()
    }

    private fun setupControls() {
        btnRotateRatio.setOnClickListener {
            toggleOrientationRatio()
        }

        btnMinimize.setOnClickListener {
            onMinimizeRequested()
        }

        btnMaximize.setOnClickListener {
            toggleMaximize()
        }

        btnClose.setOnClickListener {
            onCloseRequested()
        }
    }

    fun setWindowFocusable(focusable: Boolean) {
        isKeyboardFocusEnabled = focusable
        if (focusable) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        updateLayout()
    }

    private fun toggleMaximize() {
        val displayMetrics = context.resources.displayMetrics
        val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels
        if (!isMaximized) {
            savedWidth = layoutParams.width
            savedHeight = layoutParams.height
            savedX = layoutParams.x
            savedY = layoutParams.y

            val maxH = (displayMetrics.heightPixels * (if (isLandscape) 0.90 else 0.85)).toInt()
            val maxW = min((displayMetrics.widthPixels * 0.94).toInt(), (maxH * currentAspectRatio).toInt())

            layoutParams.width = maxW
            layoutParams.height = (maxW / currentAspectRatio).toInt()
            layoutParams.x = (displayMetrics.widthPixels - maxW) / 2
            layoutParams.y = (displayMetrics.heightPixels - layoutParams.height) / 2
            isMaximized = true
        } else {
            val maxX = max(0, displayMetrics.widthPixels - savedWidth)
            val maxY = max(0, displayMetrics.heightPixels - savedHeight)

            layoutParams.width = savedWidth.coerceIn(200, displayMetrics.widthPixels)
            layoutParams.height = savedHeight.coerceIn(350, displayMetrics.heightPixels)
            layoutParams.x = savedX.coerceIn(0, maxX)
            layoutParams.y = savedY.coerceIn(0, maxY)
            isMaximized = false
        }
        virtualDisplayManager.resize(layoutParams.width, layoutParams.height, 320)
        touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)
        updateLayout()
    }

    fun applyAspectRatio(isLandscape: Boolean) {
        isLandscapeRatio = isLandscape
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        if (isLandscapeRatio) {
            // 16:9 Landscape Gaming Mode (e.g. Free Fire, BGMI, COD, Widescreen media)
            val targetW = min((screenWidth * 0.92).toInt(), (screenHeight * 1.55f).toInt())
            val targetH = (targetW / ASPECT_RATIO_16_9).toInt().coerceAtMost((screenHeight * 0.86).toInt())
            val finalW = (targetH * ASPECT_RATIO_16_9).toInt()

            layoutParams.width = finalW
            layoutParams.height = targetH
            layoutParams.x = (screenWidth - finalW) / 2
            layoutParams.y = (screenHeight - targetH) / 2
            btnRotateRatio.setColorFilter(context.getColor(R.color.accent))
        } else {
            // 9:16 Portrait Mode (e.g. Instagram, WhatsApp, TikTok, standard mobile apps)
            val isScreenLandscape = screenWidth > screenHeight
            val safeHeight = if (isScreenLandscape) {
                (screenHeight * 0.84).toInt()
            } else {
                val preferredWidth = preferencesManager.windowWidth.coerceAtLeast(320)
                val safeWidth = min(preferredWidth, (screenWidth * 0.90).toInt())
                (safeWidth / ASPECT_RATIO_9_16).toInt().coerceAtMost((screenHeight * 0.80).toInt())
            }
            val finalW = (safeHeight * ASPECT_RATIO_9_16).toInt()

            layoutParams.width = finalW
            layoutParams.height = safeHeight
            layoutParams.x = (screenWidth - finalW) / 2
            layoutParams.y = if (isScreenLandscape) (screenHeight - safeHeight) / 2 else (screenHeight * 0.08).toInt()
            btnRotateRatio.clearColorFilter()
        }

        preferencesManager.windowWidth = layoutParams.width
        preferencesManager.windowHeight = layoutParams.height
        virtualDisplayManager.resize(layoutParams.width, layoutParams.height, 320)
        touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)
        updateLayout()
    }

    fun toggleOrientationRatio() {
        applyAspectRatio(!isLandscapeRatio)
        showResizeBadge(layoutParams.width, layoutParams.height)
        view.postDelayed({
            hideResizeBadge()
        }, 1200)
    }

    fun launchAppInWindow(packageName: String, appName: String, icon: Drawable?) {
        tvHeaderTitle.text = appName
        if (icon != null) {
            ivHeaderIcon.setImageDrawable(icon)
        } else {
            ivHeaderIcon.setImageResource(R.mipmap.ic_launcher)
        }

        cleanContent()

        // Auto-detect environment:
        // 1. Is the current screen/device in landscape mode (e.g. game running or phone rotated horizontally)?
        // 2. Or is the target app specifically a landscape game (Free Fire, BGMI, COD, etc.)?
        val displayMetrics = context.resources.displayMetrics
        val isScreenLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isAppLandscape = com.floating.virtualwindow.data.WebAppCatalog.isLandscapeApp(context, packageName)

        // Automatically open in landscape if device is landscape OR if it's a landscape app
        applyAspectRatio(isScreenLandscape || isAppLandscape)

        // 1. Check if user is in Zero Setup (Web) mode and app has a web version
        val webFallback = com.floating.virtualwindow.data.WebAppCatalog.resolveWebApp(context, packageName)
        if (preferencesManager.engineMode == PreferencesManager.MODE_ZERO_SETUP && webFallback != null) {
            openBrowser(webFallback.url, appName, icon = icon, asDesktop = webFallback.asDesktop, forceRatioCheck = false)
            return
        }

        // 2. If Shizuku is active, launch real native app in Freeform Floating Window Mode!
        if (GuestLauncher.isShizukuAvailable()) {
            val launched = GuestLauncher.launchViaFreeformShizuku(context, packageName)
            if (launched) {
                close()
                Toast.makeText(context, "$appName opened in floating window", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 2b. If Shizuku is running but Orbis lacks permission, prompt immediately
        if (GuestLauncher.isShizukuRunningWithoutPermission()) {
            GuestLauncher.requestShizukuPermission()
            Toast.makeText(context, "Grant Shizuku permission to open $appName", Toast.LENGTH_LONG).show()
        }

        // 3. Fallback to Web version if available
        if (webFallback != null) {
            Toast.makeText(context, "Using Web mode for $appName", Toast.LENGTH_SHORT).show()
            openBrowser(webFallback.url, appName, icon = icon, asDesktop = webFallback.asDesktop, forceRatioCheck = false)
            return
        }

        // 4. Secondary fallback: native system Freeform (e.g. Samsung Pop-up view / Stock Freeform)
        val launchedFreeform = com.floating.virtualwindow.engine.FreeformLauncher.launchAppInFreeform(context, packageName)
        if (launchedFreeform) {
            close()
            return
        }

        // 5. No web version and no freeform available -> Show Setup Required in Orbis window
        showSetupRequired(packageName, appName)
    }

    private fun handleNativeLaunchFailure(packageName: String, appName: String, icon: Drawable?) {
        view.post {
            cleanContent()
            val webFallback = com.floating.virtualwindow.data.WebAppCatalog.resolveWebApp(context, packageName)
            if (webFallback != null) {
                Toast.makeText(context, "Native mode unavailable · Using Web", Toast.LENGTH_SHORT).show()
                openBrowser(webFallback.url, appName, icon = icon, asDesktop = webFallback.asDesktop, forceRatioCheck = false)
            } else {
                showSetupRequired(packageName, appName)
            }
        }
    }

    private fun showSetupRequired(packageName: String, appName: String) {
        show()
        cleanContent()
        val setupView = LayoutInflater.from(context).inflate(R.layout.view_setup_required, contentContainer, false)
        val tvTitle = setupView.findViewById<TextView>(R.id.tvSetupRequiredTitle)
        val tvDesc = setupView.findViewById<TextView>(R.id.tvSetupRequiredDesc)
        tvTitle.text = "$appName: Setup Required"
        tvDesc.text = context.getString(R.string.setup_required_desc)

        setupView.findViewById<Button>(R.id.btnOpenSetupGuide)?.setOnClickListener {
            if (GuestLauncher.isShizukuRunningWithoutPermission()) {
                GuestLauncher.requestShizukuPermission()
            } else {
                WirelessGuideDialog(context).show()
            }
        }

        setupView.findViewById<Button>(R.id.btnLaunchAppNormally)?.setOnClickListener {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
            minimize()
        }

        contentContainer.addView(setupView)
    }

    private fun setupVirtualDisplay(packageName: String, appName: String, icon: Drawable? = null) {
        val surfaceView = SurfaceView(context)
        currentSurfaceView = surfaceView
        contentContainer.addView(surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val displayId = virtualDisplayManager.createVirtualDisplay(
                    "VirtualWindowDisplay",
                    layoutParams.width,
                    layoutParams.height,
                    320,
                    holder.surface
                )
                if (displayId <= 0) {
                    handleNativeLaunchFailure(packageName, appName, icon)
                    return
                }

                touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)

                val launched = GuestLauncher.launchApp(context, packageName, displayId) { name, url ->
                    openBrowser(url, name, icon = icon, forceRatioCheck = false)
                }

                if (!launched) {
                    handleNativeLaunchFailure(packageName, appName, icon)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                virtualDisplayManager.resize(width, height, 320)
                touchForwarder.updateDimensions(width, height, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                virtualDisplayManager.release()
            }
        })

        surfaceView.setOnTouchListener { _, event ->
            val displayId = virtualDisplayManager.displayId
            if (displayId > 0) {
                touchForwarder.forwardTouch(event, displayId)
            }
            true
        }
    }

    fun openBrowser(
        url: String,
        title: String = "Browser",
        icon: Drawable? = null,
        asDesktop: Boolean = false,
        forceRatioCheck: Boolean = true
    ) {
        show()
        tvHeaderTitle.text = title
        if (icon != null) {
            ivHeaderIcon.setImageDrawable(icon)
        } else {
            ivHeaderIcon.setImageResource(R.drawable.ic_browser)
        }
        cleanContent()

        if (forceRatioCheck) {
            val displayMetrics = context.resources.displayMetrics
            val isScreenLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels ||
                    context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            applyAspectRatio(isScreenLandscape)
        }

        val browserView = FloatingBrowserView(context)
        currentBrowserView = browserView
        browserView.onFocusChanged = { hasFocus ->
            setWindowFocusable(hasFocus)
        }
        contentContainer.addView(browserView)
        browserView.loadUrl(url, asDesktop)
    }

    fun openCalculator() {
        show()
        tvHeaderTitle.text = "Calculator"
        ivHeaderIcon.setImageResource(R.drawable.ic_calculator)
        cleanContent()

        val displayMetrics = context.resources.displayMetrics
        val isScreenLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyAspectRatio(isScreenLandscape)

        val calcView = FloatingCalculatorView(context)
        currentCalculatorView = calcView
        contentContainer.addView(calcView)
    }

    private fun cleanContent() {
        currentBrowserView?.destroy()
        currentBrowserView = null
        currentCalculatorView = null
        currentSurfaceView = null
        virtualDisplayManager.release()
        contentContainer.removeAllViews()
        setWindowFocusable(false)
    }

    private fun updateLayout() {
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Strictly bounds and centers the window within the visible screen dimensions.
     * Prevents window dimensions or right-edge controls (Close, Minimize) from ever being pushed off-screen.
     */
    private fun clampWindowToScreen(screenWidth: Int, screenHeight: Int, isLandscape: Boolean) {
        val maxSafeW = (screenWidth * 0.94).toInt()
        val maxSafeH = (screenHeight * (if (isLandscape) 0.88 else 0.82)).toInt()

        if (isLandscapeRatio) {
            // 16:9 widescreen mode: bounded primarily by maxSafeW
            val targetW = min(maxSafeW, (maxSafeH * ASPECT_RATIO_16_9).toInt())
            val targetH = (targetW / ASPECT_RATIO_16_9).toInt()
            layoutParams.width = targetW.coerceIn(280, maxSafeW)
            layoutParams.height = targetH.coerceIn(200, maxSafeH)
        } else {
            // 9:16 portrait mode: bounded primarily by maxSafeH
            val targetH = min(maxSafeH, (maxSafeW / ASPECT_RATIO_9_16).toInt())
            val targetW = (targetH * ASPECT_RATIO_9_16).toInt()
            layoutParams.height = targetH.coerceIn(320, maxSafeH)
            layoutParams.width = targetW.coerceIn(200, maxSafeW)
        }

        // Hard guarantee: width and height NEVER exceed screen boundaries
        layoutParams.width = layoutParams.width.coerceIn(200, maxSafeW)
        layoutParams.height = layoutParams.height.coerceIn(200, maxSafeH)

        val maxX = max(0, screenWidth - layoutParams.width)
        val maxY = max(0, screenHeight - layoutParams.height)
        layoutParams.x = ((screenWidth - layoutParams.width) / 2).coerceIn(0, maxX)
        layoutParams.y = ((screenHeight - layoutParams.height) / 2).coerceIn(0, maxY)
    }

    fun show() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = screenWidth > screenHeight ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        clampWindowToScreen(screenWidth, screenHeight, isLandscape)

        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            updateLayout()
        }
    }

    /**
     * Called when the device orientation changes (Portrait <-> Landscape).
     * Automatically adapts aspect ratio to new orientation and ensures window is perfectly clamped.
     */
    fun handleOrientationChanged() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isScreenLandscape = screenWidth > screenHeight ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscapeRatio != isScreenLandscape) {
            applyAspectRatio(isScreenLandscape)
        } else {
            clampWindowToScreen(screenWidth, screenHeight, isScreenLandscape)
            virtualDisplayManager.resize(layoutParams.width, layoutParams.height, 320)
            touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)
            updateLayout()
        }
    }

    /**
     * Non-destructive hide for minimizing into a bubble.
     * Preserves the active WebView, scroll position, and running session intact.
     */
    fun minimize() {
        setWindowFocusable(false)
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Restores the minimized window without reloading or losing state.
     * Automatically adapts aspect ratio if screen orientation changed while in bubble.
     */
    fun restore() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isScreenLandscape = screenWidth > screenHeight ||
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscapeRatio != isScreenLandscape) {
            applyAspectRatio(isScreenLandscape)
        } else {
            clampWindowToScreen(screenWidth, screenHeight, isScreenLandscape)
        }
        show()
    }

    /**
     * Completely closes the window and releases resources.
     */
    fun close() {
        cleanContent()
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        close()
    }

    companion object {
        const val ASPECT_RATIO_9_16 = 9.0f / 16.0f
        const val ASPECT_RATIO_16_9 = 16.0f / 9.0f
    }
}
