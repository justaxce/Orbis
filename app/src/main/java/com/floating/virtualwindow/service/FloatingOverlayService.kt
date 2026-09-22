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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.floating.virtualwindow.MainActivity
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.overlay.EdgeHandleView
import com.floating.virtualwindow.overlay.FloatingBubbleView
import com.floating.virtualwindow.overlay.FloatingWindowView
import com.floating.virtualwindow.overlay.SidebarDockView
import rikka.shizuku.Shizuku

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager

    private var edgeHandleView: EdgeHandleView? = null
    private var sidebarDockView: SidebarDockView? = null
    private var floatingWindowView: FloatingWindowView? = null
    private var floatingBubbleView: FloatingBubbleView? = null

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
                NotificationManager.IMPORTANCE_LOW
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

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Sidebar Active")
            .setContentText("Edge handle is ready. Tap to open settings or manage apps.")
            .setSmallIcon(R.drawable.ic_sidebar_handle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
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
        )

        floatingBubbleView = FloatingBubbleView(this, windowManager) {
            // Bubble tapped -> Restore window without reloading
            floatingBubbleView?.hide()
            floatingWindowView?.restore()
        }

        // Show edge handle by default
        edgeHandleView?.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                edgeHandleView?.show()
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
                edgeHandleView?.show()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingWindowView?.handleOrientationChanged()
        edgeHandleView?.handleOrientationChanged()
        sidebarDockView?.handleOrientationChanged()
        floatingBubbleView?.handleOrientationChanged()
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        edgeHandleView?.hide()
        sidebarDockView?.hide()
        floatingWindowView?.close()
        floatingBubbleView?.hide()
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
