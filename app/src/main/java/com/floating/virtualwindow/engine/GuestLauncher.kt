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
        val prefs = PreferencesManager(context)

        // If Shizuku / Wireless Debugging is active, ALWAYS launch the real native app!
        if (isShizukuAvailable()) {
            val launched = launchViaShizuku(context, packageName, targetDisplayId)
            if (launched) return true
        }

        // Otherwise, check if app has a web fallback
        val webInfo = com.floating.virtualwindow.data.WebAppCatalog.resolveWebApp(context, packageName)
        if (webInfo != null) {
            onWebFallbackRequested?.invoke(webInfo.title, webInfo.url)
            return true
        }

        // 2. Try launching in Freeform / Pop-up view mode
        val launchedFreeform = FreeformLauncher.launchAppInFreeform(context, packageName)
        if (launchedFreeform) return true

        // 3. Fallback: Launch in-process stub on the virtual display
        return try {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = targetDisplayId
            }
            val stubIntent = Intent(context, GuestStubActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra("EXTRA_TARGET_PACKAGE", packageName)
            }
            context.startActivity(stubIntent, options.toBundle())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun launchViaShizuku(context: Context, packageName: String, displayId: Int): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

            // Using Shizuku to start activity on target display
            val cmd = "am start -d ${displayId} -n ${launchIntent.component?.flattenToString()}"
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
            process?.waitFor()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
