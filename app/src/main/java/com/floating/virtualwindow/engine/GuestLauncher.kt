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

    fun launchApp(
        context: Context,
        packageName: String,
        targetDisplayId: Int,
        onWebFallbackRequested: ((String, String) -> Unit)? = null
    ): Boolean {
        // If Shizuku / Wireless Debugging is active and we have a valid virtual display, attempt real native launch
        if (targetDisplayId > 0 && isShizukuAvailable()) {
            val launched = launchViaShizuku(context, packageName, targetDisplayId)
            if (launched) return true
        }

        // Native launch unavailable or failed -> check if app has a web fallback
        val webInfo = com.floating.virtualwindow.data.WebAppCatalog.resolveWebApp(context, packageName)
        if (webInfo != null) {
            onWebFallbackRequested?.invoke(webInfo.title, webInfo.url)
            return true
        }

        // Secondary fallback: Freeform / Pop-up view mode if supported
        val launchedFreeform = FreeformLauncher.launchAppInFreeform(context, packageName)
        if (launchedFreeform) return true

        // Do not substitute with a fake GuestStubActivity; return false so the caller
        // can gracefully route to Web or guide the user
        return false
    }

    private fun launchViaShizuku(context: Context, packageName: String, displayId: Int): Boolean {
        if (displayId <= 0) return false
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
            val component = launchIntent.component ?: return false
            val componentName = component.flattenToString()

            // Correct Android am syntax: --display <id>
            val cmd = "am start --display $displayId -n $componentName"
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
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

            // Verify stdout/stderr for known failure tokens
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
