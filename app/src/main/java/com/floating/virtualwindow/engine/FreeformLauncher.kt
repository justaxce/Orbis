package com.floating.virtualwindow.engine

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build

object FreeformLauncher {

    fun launchAppInFreeform(
        context: Context,
        packageName: String,
        bounds: Rect? = null
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)

        val options = ActivityOptions.makeBasic()

        // Set windowing mode to freeform (5) via reflection
        try {
            val setWindowingModeMethod = ActivityOptions::class.java.getMethod(
                "setLaunchWindowingMode",
                Int::class.javaPrimitiveType
            )
            setWindowingModeMethod.invoke(options, 5)
        } catch (e: Exception) {
            // Hidden API fallback
        }

        val defaultBounds = bounds ?: Rect(150, 200, 950, 1600)
        options.launchBounds = defaultBounds

        return try {
            context.startActivity(launchIntent, options.toBundle())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
