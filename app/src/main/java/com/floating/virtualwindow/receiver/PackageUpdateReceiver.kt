package com.floating.virtualwindow.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.floating.virtualwindow.MainActivity
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.service.FloatingOverlayService

class PackageUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageUpdateReceiver"
        private const val CHANNEL_ID = "orbis_update_channel"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Broadcast received: $action")

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(context)) {
                try {
                    FloatingOverlayService.start(context)
                    Log.d(TAG, "Successfully triggered FloatingOverlayService.start()")
                } catch (e: Exception) {
                    Log.w(TAG, "Direct service start restricted by OS: ${e.message}. Posting restore notification.")
                    postRestoreNotification(context)
                }
            }
        }
    }

    private fun postRestoreNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Orbis Updates & Restoration",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Restores floating sidebar after system or app updates"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sidebar_handle)
                .setContentTitle("Orbis Updated")
                .setContentText("Tap to restore your floating sidebar")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting restore notification", e)
        }
    }
}
