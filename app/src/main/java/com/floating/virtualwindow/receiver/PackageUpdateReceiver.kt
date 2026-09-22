package com.floating.virtualwindow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.service.FloatingOverlayService

class PackageUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(context)) {
                FloatingOverlayService.start(context)
            }
        }
    }
}
