package com.floating.virtualwindow.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.floating.virtualwindow.MainActivity
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.AppRepository
import com.floating.virtualwindow.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SidebarDockView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onAppSelected: (packageName: String, appName: String, icon: Drawable?) -> Unit,
    private val onToolBrowserSelected: () -> Unit,
    private val onToolCalculatorSelected: () -> Unit,
    private val onDismissRequested: () -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_sidebar_dock, null)
    private val layoutParams: WindowManager.LayoutParams
    private val preferencesManager = PreferencesManager(context)
    private val appRepository = AppRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val llAppsContainer: LinearLayout = view.findViewById(R.id.llAppsContainer)

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val isRightSide = preferencesManager.dockSide == "RIGHT"
        val gravity = Gravity.CENTER_VERTICAL or (if (isRightSide) Gravity.END else Gravity.START)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = 0
        }

        setupQuickTools()
        setupOutsideTouch()
    }

    private fun setupQuickTools() {
        view.findViewById<View>(R.id.btnToolBrowser)?.setOnClickListener {
            onToolBrowserSelected()
            hide()
        }

        view.findViewById<View>(R.id.btnToolCalculator)?.setOnClickListener {
            onToolCalculatorSelected()
            hide()
        }

        view.findViewById<View>(R.id.btnAddApps)?.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            hide()
        }
    }

    private fun setupOutsideTouch() {
        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                onDismissRequested()
                true
            } else {
                false
            }
        }
    }

    fun populateApps() {
        scope.launch {
            val selectedApps = appRepository.getSelectedApps()
            withContext(Dispatchers.Main) {
                llAppsContainer.removeAllViews()
                val inflater = LayoutInflater.from(context)

                for (app in selectedApps) {
                    val itemView = inflater.inflate(R.layout.item_sidebar_app, llAppsContainer, false)
                    val ivIcon = itemView.findViewById<ImageView>(R.id.ivSidebarIcon)
                    val tvName = itemView.findViewById<TextView>(R.id.tvSidebarName)
                    tvName.text = app.appName
                    val displayIcon = app.icon ?: com.floating.virtualwindow.data.WebIconHelper.getIconForApp(context, app.appName, app.packageName)
                    ivIcon.setImageDrawable(displayIcon)

                    itemView.setOnClickListener {
                        onAppSelected(app.packageName, app.appName, displayIcon)
                        hide()
                    }

                    llAppsContainer.addView(itemView)
                }
            }
        }
    }

    fun show() {
        val displayMetrics = context.resources.displayMetrics
        layoutParams.height = (displayMetrics.heightPixels * 0.90).toInt()
        populateApps()
        if (view.parent == null) {
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleOrientationChanged() {
        val displayMetrics = context.resources.displayMetrics
        layoutParams.height = (displayMetrics.heightPixels * 0.90).toInt()
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateDockSide(dockSide: String) {
        val isRightSide = dockSide == "RIGHT"
        layoutParams.gravity = Gravity.CENTER_VERTICAL or (if (isRightSide) Gravity.END else Gravity.START)
        layoutParams.x = 0
        if (view.parent != null) {
            try {
                windowManager.updateViewLayout(view, layoutParams)
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
