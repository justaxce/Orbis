package com.floating.virtualwindow.engine

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import com.floating.virtualwindow.data.PreferencesManager
import rikka.shizuku.Shizuku

object GuestLauncher {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuRunningWithoutPermission(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 8001) {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchApp(
        context: Context,
        packageName: String,
        targetDisplayId: Int = 0,
        onWebFallbackRequested: ((String, String) -> Unit)? = null
    ): Boolean {
        // 1. If Shizuku is active, launch real native app in Freeform Floating Window Mode
        if (isShizukuAvailable()) {
            val launched = launchViaFreeformShizuku(context, packageName)
            if (launched) return true
        }

        // 2. Native launch unavailable or failed -> check if app has a web fallback
        val webInfo = com.floating.virtualwindow.data.WebAppCatalog.resolveWebApp(context, packageName)
        if (webInfo != null) {
            onWebFallbackRequested?.invoke(webInfo.title, webInfo.url)
            return true
        }

        // 3. Secondary fallback: Native Freeform / Pop-up view mode if supported
        val launchedFreeform = FreeformLauncher.launchAppInFreeform(context, packageName)
        if (launchedFreeform) return true

        return false
    }

    fun launchViaFreeformShizuku(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
            val component = launchIntent.component ?: return false
            val componentName = component.flattenToString()

            // 1. Enable freeform windowing support and force resizable activities globally via privileged shell
            // 2. Launch in windowingMode 5 (freeform) with FLAG_ACTIVITY_NEW_TASK (0x10000000) and FLAG_ACTIVITY_MULTIPLE_TASK (0x08000000) = 0x18000000
            val enableCmd = "settings put global enable_freeform_support 1; settings put secure force_resizable_activities 1"
            val launchCmd = "am start --windowingMode 5 -f 0x18000000 -n $componentName"
            val fullCmd = "$enableCmd; $launchCmd"

            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = method.invoke(null, arrayOf("sh", "-c", fullCmd), null, null) as? Process
                ?: return false

            val completed = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                return false
            }

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.exitValue()

            if (exitCode != 0) {
                return false
            }

            if (stdout.contains("Error:") || stdout.contains("Exception") ||
                stderr.contains("Error:") || stderr.contains("Exception") || stderr.contains("Permission Denial")) {
                return false
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
