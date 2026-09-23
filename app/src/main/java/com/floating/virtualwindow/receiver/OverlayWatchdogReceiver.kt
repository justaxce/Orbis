package com.floating.virtualwindow.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.service.FloatingOverlayService

/**
 * Watchdog broadcast receiver dedicated to keeping the Orbis floating overlay alive:
 * - Survives task kills when user swipes the app out of the Recents screen.
 * - Resurrects the overlay if killed by the OS Low Memory Killer.
 * - Auto-restores the overlay on device unlock (USER_PRESENT) and system boot.
 */
class OverlayWatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Watchdog ping received: $action")

        val preferencesManager = PreferencesManager(context)
        if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(context)) {
            try {
                FloatingOverlayService.start(context)
                Log.d(TAG, "Successfully kept FloatingOverlayService alive via watchdog")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FloatingOverlayService from watchdog: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "OverlayWatchdog"
        const val ACTION_RESTART_OVERLAY = "com.floating.virtualwindow.ACTION_RESTART_OVERLAY"
        private const val WATCHDOG_REQUEST_CODE = 9901

        /**
         * Schedules an exact AlarmManager alarm that triggers [OverlayWatchdogReceiver]
         * to wake up the process and resurrect [FloatingOverlayService] if it was killed.
         */
        fun scheduleResurrection(context: Context, delayMs: Long = 500L) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context.applicationContext, OverlayWatchdogReceiver::class.java).apply {
                    action = ACTION_RESTART_OVERLAY
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context.applicationContext,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val triggerAt = System.currentTimeMillis() + delayMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
                Log.d(TAG, "Resurrection alarm scheduled for +${delayMs}ms")
            } catch (e: Exception) {
                Log.w(TAG, "Could not set exact alarm, falling back to non-exact: ${e.message}")
                try {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                    val intent = Intent(context.applicationContext, OverlayWatchdogReceiver::class.java).apply {
                        action = ACTION_RESTART_OVERLAY
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context.applicationContext,
                        WATCHDOG_REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pendingIntent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }
}
