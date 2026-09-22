package com.floating.virtualwindow.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val preferencesManager = PreferencesManager(context)

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        val selectedPackages = preferencesManager.getSelectedPackages()

        val apps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            // Exclude our own app
            if (packageName == context.packageName || seenPackages.contains(packageName)) {
                continue
            }
            seenPackages.add(packageName)

            val appName = resolveInfo.loadLabel(packageManager).toString()
            val icon = try {
                resolveInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }
            val isSystem = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                false
            }
            val hasWeb = WebAppCatalog.hasWebVersion(context, packageName)

            apps.add(
                AppInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    isSelected = selectedPackages.contains(packageName),
                    isSystemApp = isSystem,
                    hasWebVersion = hasWeb
                )
            )
        }

        // Sort: Selected apps first, then alphabetical by name
        apps.sortWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
        apps
    }

    suspend fun getSelectedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val allApps = getInstalledApps()
        val selectedSet = preferencesManager.getSelectedPackages()
        allApps.filter { selectedSet.contains(it.packageName) }
    }
}
