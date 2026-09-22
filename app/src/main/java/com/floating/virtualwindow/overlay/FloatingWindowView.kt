package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.display.VirtualDisplayManager
import com.floating.virtualwindow.engine.GuestLauncher
import com.floating.virtualwindow.engine.TouchForwarder
import com.floating.virtualwindow.tools.FloatingBrowserView
import com.floating.virtualwindow.tools.FloatingCalculatorView
import com.floating.virtualwindow.ui.WirelessGuideDialog
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
    private val btnKeyboard: ImageButton = view.findViewById(R.id.btnKeyboard)
    private val btnMinimize: ImageButton = view.findViewById(R.id.btnMinimize)
    private val btnMaximize: ImageButton = view.findViewById(R.id.btnMaximize)
    private val btnClose: ImageButton = view.findViewById(R.id.btnClose)
    private val contentContainer: FrameLayout = view.findViewById(R.id.windowContentContainer)
    private val ivResizeHandle: ImageView = view.findViewById(R.id.ivResizeHandle)

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
        val safeInitialWidth = min(preferencesManager.windowWidth, (displayMetrics.widthPixels * 0.90).toInt())
        val safeInitialHeight = min(preferencesManager.windowHeight, (displayMetrics.heightPixels * 0.80).toInt())
        val safeInitialX = (displayMetrics.widthPixels * 0.05).toInt()
        val safeInitialY = (displayMetrics.heightPixels * 0.08).toInt()

        // Important: Use FLAG_NOT_FOCUSABLE by default so underlying apps (WhatsApp, Instagram, etc.)
        // retain 100% input and soft keyboard (IME) capability without interference.
        // Omit FLAG_LAYOUT_NO_LIMITS so window bounds are properly constrained to the screen.
        layoutParams = WindowManager.LayoutParams(
            safeInitialWidth,
            safeInitialHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
        ivResizeHandle.setOnTouchListener { _, event ->
            val displayMetrics = context.resources.displayMetrics
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = layoutParams.width
                    initialHeight = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val minW = (displayMetrics.widthPixels * 0.40).toInt().coerceAtLeast(320)
                    val minH = (displayMetrics.heightPixels * 0.30).toInt().coerceAtLeast(420)
                    val maxW = (displayMetrics.widthPixels - layoutParams.x).coerceAtLeast(minW)
                    val maxH = (displayMetrics.heightPixels - layoutParams.y).coerceAtLeast(minH)

                    val newWidth = (initialWidth + (event.rawX - initialTouchX).toInt()).coerceIn(minW, maxW)
                    val newHeight = (initialHeight + (event.rawY - initialTouchY).toInt()).coerceIn(minH, maxH)

                    layoutParams.width = newWidth
                    layoutParams.height = newHeight
                    preferencesManager.windowWidth = newWidth
                    preferencesManager.windowHeight = newHeight

                    virtualDisplayManager.resize(newWidth, newHeight, 320)
                    touchForwarder.updateDimensions(newWidth, newHeight, newWidth, newHeight)
                    updateLayout()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupControls() {
        btnKeyboard.setOnClickListener {
            val newFocus = !isKeyboardFocusEnabled
            setWindowFocusable(newFocus)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (newFocus) {
                imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            } else {
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
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
            btnKeyboard.setColorFilter(context.getColor(R.color.accent))
        } else {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            btnKeyboard.clearColorFilter()
        }
        updateLayout()
    }

    private fun toggleMaximize() {
        val displayMetrics = context.resources.displayMetrics
        if (!isMaximized) {
            savedWidth = layoutParams.width
            savedHeight = layoutParams.height
            savedX = layoutParams.x
            savedY = layoutParams.y

            layoutParams.width = (displayMetrics.widthPixels * 0.94).toInt()
            layoutParams.height = (displayMetrics.heightPixels * 0.85).toInt()
            layoutParams.x = (displayMetrics.widthPixels * 0.03).toInt()
            layoutParams.y = (displayMetrics.heightPixels * 0.06).toInt()
            isMaximized = true
        } else {
            val maxX = max(0, displayMetrics.widthPixels - savedWidth)
            val maxY = max(0, displayMetrics.heightPixels - savedHeight)

            layoutParams.width = savedWidth.coerceIn(320, displayMetrics.widthPixels)
            layoutParams.height = savedHeight.coerceIn(420, displayMetrics.heightPixels)
            layoutParams.x = savedX.coerceIn(0, maxX)
            layoutParams.y = savedY.coerceIn(0, maxY)
            isMaximized = false
        }
        virtualDisplayManager.resize(layoutParams.width, layoutParams.height, 320)
        touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)
        updateLayout()
    }

    fun launchAppInWindow(packageName: String, appName: String, icon: Drawable?) {
        tvHeaderTitle.text = appName
        if (icon != null) {
            ivHeaderIcon.setImageDrawable(icon)
        } else {
            ivHeaderIcon.setImageResource(R.mipmap.ic_launcher)
        }

        cleanContent()

        // Check if we should open via Web engine or VirtualDisplay
        val prefs = PreferencesManager(context)
        if (prefs.engineMode == PreferencesManager.MODE_ZERO_SETUP) {
            when (packageName) {
                "com.whatsapp" -> {
                    openBrowser("https://web.whatsapp.com", "WhatsApp Web", asDesktop = true)
                    return
                }
                "com.instagram.android" -> {
                    openBrowser("https://www.instagram.com", "Instagram", asDesktop = false)
                    return
                }
                "com.android.chrome" -> {
                    openBrowser("https://www.google.com", "Browser", asDesktop = false)
                    return
                }
            }
        }

        // If Wireless Debugging / Shizuku is not available, do not show a blank screen!
        if (!GuestLauncher.isShizukuAvailable()) {
            showSetupRequired(packageName, appName)
            return
        }

        // Open in Virtual Display
        setupVirtualDisplay(packageName)
    }

    private fun showSetupRequired(packageName: String, appName: String) {
        cleanContent()
        val setupView = LayoutInflater.from(context).inflate(R.layout.view_setup_required, contentContainer, false)
        val tvTitle = setupView.findViewById<TextView>(R.id.tvSetupRequiredTitle)
        val tvDesc = setupView.findViewById<TextView>(R.id.tvSetupRequiredDesc)
        tvTitle.text = "$appName: Setup Required"
        tvDesc.text = context.getString(R.string.setup_required_desc)

        setupView.findViewById<Button>(R.id.btnOpenSetupGuide)?.setOnClickListener {
            WirelessGuideDialog(context).show()
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

    private fun setupVirtualDisplay(packageName: String) {
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
                touchForwarder.updateDimensions(layoutParams.width, layoutParams.height, layoutParams.width, layoutParams.height)

                GuestLauncher.launchApp(context, packageName, displayId) { name, url ->
                    openBrowser(url, name)
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
            touchForwarder.forwardTouch(event, virtualDisplayManager.displayId)
            true
        }
    }

    fun openBrowser(url: String, title: String = "Browser", asDesktop: Boolean = false) {
        tvHeaderTitle.text = title
        ivHeaderIcon.setImageResource(R.drawable.ic_browser)
        cleanContent()

        val browserView = FloatingBrowserView(context)
        currentBrowserView = browserView
        browserView.onFocusChanged = { hasFocus ->
            setWindowFocusable(hasFocus)
        }
        contentContainer.addView(browserView)
        browserView.loadUrl(url, asDesktop)
    }

    fun openCalculator() {
        tvHeaderTitle.text = "Calculator"
        ivHeaderIcon.setImageResource(R.drawable.ic_calculator)
        cleanContent()

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

    fun show() {
        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
     */
    fun restore() {
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
}
