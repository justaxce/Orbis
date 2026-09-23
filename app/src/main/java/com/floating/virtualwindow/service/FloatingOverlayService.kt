package com.floating.virtualwindow.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.floating.virtualwindow.MainActivity
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.overlay.AllAppsDrawerView
import com.floating.virtualwindow.overlay.BubbleDismissTargetView
import com.floating.virtualwindow.overlay.EdgeHandleView
import com.floating.virtualwindow.overlay.FloatingBubbleView
import com.floating.virtualwindow.overlay.FloatingWindowView
import com.floating.virtualwindow.overlay.SidebarDockView
import com.floating.virtualwindow.receiver.OverlayWatchdogReceiver
import com.floating.virtualwindow.tools.torch.TorchHelper
import rikka.shizuku.Shizuku

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager

    private var edgeHandleView: EdgeHandleView? = null
    private var sidebarDockView: SidebarDockView? = null
    private var floatingWindowView: FloatingWindowView? = null
    private var secondaryWindowView: FloatingWindowView? = null
    private var allAppsDrawerView: AllAppsDrawerView? = null
    private var floatingBubbleView: FloatingBubbleView? = null
    private var bubbleDismissTargetView: BubbleDismissTargetView? = null

    private var lastAppIcon: Drawable? = null

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        // Shizuku binder received in service
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)

        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startForegroundNotification()
        initializeOverlayViews()
    }

    private fun startForegroundNotification() {
        val channelId = "floating_sidebar_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Floating Sidebar Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps the floating sidebar active over any application"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_START
        }
        val restartPendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Sidebar Active")
            .setContentText("Edge handle is ready. Tap to open settings or manage apps.")
            .setSmallIcon(R.drawable.ic_sidebar_handle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_refresh, "Restart Handle", restartPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun initializeOverlayViews() {
        edgeHandleView = EdgeHandleView(
            context = this,
            windowManager = windowManager,
            onHandleTapped = {
                // Handle tapped -> Open Sidebar Dock
                sidebarDockView?.show()
            },
            onDockSideChanged = { newSide ->
                // Magnetic edge snap -> re-anchor the dock to that side
                sidebarDockView?.updateDockSide(newSide)
            }
        )

        sidebarDockView = SidebarDockView(
            context = this,
            windowManager = windowManager,
            onAppSelected = { packageName, appName, icon ->
                lastAppIcon = icon
                floatingBubbleView?.setIcon(icon)
                floatingWindowView?.launchAppInWindow(packageName, appName, icon)
            },
            onToolBrowserSelected = {
                floatingWindowView?.show()
                floatingWindowView?.openBrowser("https://www.google.com", "Browser")
            },
            onToolCalculatorSelected = {
                floatingWindowView?.show()
                floatingWindowView?.openCalculator()
            },
            onDismissRequested = {
                sidebarDockView?.hide()
            }
        )

        allAppsDrawerView = AllAppsDrawerView(this, windowManager) { packageName, appName, icon ->
            lastAppIcon = icon
            floatingBubbleView?.setIcon(icon)
            floatingWindowView?.show()
            floatingWindowView?.launchAppInWindow(packageName, appName, icon)
        }

        sidebarDockView?.onAllAppsRequested = {
            allAppsDrawerView?.show()
        }

        floatingWindowView = FloatingWindowView(
            context = this,
            windowManager = windowManager,
            onMinimizeRequested = {
                val activeIcon = floatingWindowView?.currentHeaderIcon ?: lastAppIcon
                floatingWindowView?.minimize()
                floatingBubbleView?.setIcon(activeIcon)
                floatingBubbleView?.show()
            },
            onCloseRequested = {
                floatingWindowView?.close()
                floatingBubbleView?.hide()
            }
        ).apply {
            onNotificationReceived = {
                floatingBubbleView?.setNotificationDotVisible(true)
            }
            onNewWindowRequested = {
                openSecondaryWindow()
            }
        }

        val dismissTarget = BubbleDismissTargetView(this, windowManager)
        bubbleDismissTargetView = dismissTarget

        floatingBubbleView = FloatingBubbleView(
            context = this,
            windowManager = windowManager,
            dismissTargetView = dismissTarget,
            onBubbleTapped = {
                // Bubble tapped -> Restore window without reloading
                floatingBubbleView?.setNotificationDotVisible(false)
                floatingBubbleView?.hide()
                floatingWindowView?.restore()
            },
            onBubbleDismissed = {
                // Bubble dropped onto close cross target -> close session completely!
                floatingBubbleView?.setNotificationDotVisible(false)
                floatingBubbleView?.hide()
                floatingWindowView?.close()
                Toast.makeText(this, "Closed", Toast.LENGTH_SHORT).show()
            }
        )

        // Show edge handle by default
        edgeHandleView?.ensureAttached()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                edgeHandleView?.ensureAttached()
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_APPS -> {
                sidebarDockView?.populateApps()
            }
            ACTION_UPDATE_DOCK_SIDE -> {
                val newSide = preferencesManager.dockSide
                edgeHandleView?.updateDockSide(newSide)
                sidebarDockView?.updateDockSide(newSide)
            }
            else -> {
                edgeHandleView?.ensureAttached()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(this)) {
            // User swiped the app away from Recents task switcher.
            // Schedule an immediate resurrection alarm to prevent OS task killer from terminating the overlay!
            OverlayWatchdogReceiver.scheduleResurrection(this, 500L)
        }
    }

    private fun openSecondaryWindow() {
        if (secondaryWindowView == null) {
            secondaryWindowView = FloatingWindowView(
                context = this,
                windowManager = windowManager,
                onMinimizeRequested = {
                    secondaryWindowView?.minimize()
                },
                onCloseRequested = {
                    secondaryWindowView?.close()
                    secondaryWindowView = null
                }
            ).apply {
                val dm = resources.displayMetrics
                layoutParams.x = (layoutParams.x + 80).coerceAtMost(dm.widthPixels - 320)
                layoutParams.y = (layoutParams.y + 120).coerceAtMost(dm.heightPixels - 320)
                onNewWindowRequested = {
                    Toast.makeText(this@FloatingOverlayService, "Dual window already active", Toast.LENGTH_SHORT).show()
                }
            }
        }
        secondaryWindowView?.show()
        secondaryWindowView?.openBrowser("https://www.google.com", "Window 2")
        Toast.makeText(this, "Dual window opened", Toast.LENGTH_SHORT).show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingWindowView?.handleOrientationChanged()
        floatingWindowView?.handleThemeChanged()
        secondaryWindowView?.handleOrientationChanged()
        secondaryWindowView?.handleThemeChanged()
        edgeHandleView?.handleOrientationChanged()
        sidebarDockView?.handleOrientationChanged()
        floatingBubbleView?.handleOrientationChanged()
    }

    override fun onDestroy() {
        if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(this)) {
            // Service was killed by OS (Low Memory Killer or system battery cleanup), not by user toggle!
            // Schedule resurrection alarm so sidebar handle reappears immediately.
            OverlayWatchdogReceiver.scheduleResurrection(this, 1000L)
        }
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        TorchHelper.turnOff(this)
        edgeHandleView?.hide()
        sidebarDockView?.hide()
        allAppsDrawerView?.hide()
        allAppsDrawerView = null
        secondaryWindowView?.close()
        secondaryWindowView = null
        floatingWindowView?.close()
        floatingBubbleView?.hide()
        bubbleDismissTargetView?.hide()
        bubbleDismissTargetView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.floating.virtualwindow.START"
        const val ACTION_STOP = "com.floating.virtualwindow.STOP"
        const val ACTION_REFRESH_APPS = "com.floating.virtualwindow.REFRESH_APPS"
        const val ACTION_UPDATE_DOCK_SIDE = "com.floating.virtualwindow.UPDATE_DOCK_SIDE"

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun refreshApps(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_REFRESH_APPS
            }
            context.startService(intent)
        }

        fun updateDockSide(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_UPDATE_DOCK_SIDE
            }
            context.startService(intent)
        }
    }
}
