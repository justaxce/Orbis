package com.floating.virtualwindow.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val preferencesManager = PreferencesManager(context)

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val selectedPackages = preferencesManager.getSelectedPackages()
        val apps = mutableListOf<AppInfo>()
        val seenKeys = mutableSetOf<String>()

        // 1. Process curated high-performance web apps
        val curatedApps = WebAppCatalog.CURATED_APPS
        for (curated in curatedApps) {
            val activePkg = findActivePackage(curated)
            val icon = WebIconHelper.getIconForApp(context, curated.name, activePkg)
            val isSelected = selectedPackages.contains(activePkg) ||
                    selectedPackages.contains(curated.id) ||
                    curated.alternativePackages.any { selectedPackages.contains(it) }

            apps.add(
                AppInfo(
                    packageName = activePkg,
                    appName = curated.name,
                    icon = icon,
                    isSelected = isSelected,
                    isSystemApp = false,
                    hasWebVersion = true
                )
            )

            seenKeys.add(activePkg)
            seenKeys.add(curated.id)
            seenKeys.addAll(curated.alternativePackages)
        }

        // 2. Discover any additional installed device apps that have verified web endpoints
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfoList = packageManager.queryIntentActivities(intent, 0)

            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName || seenKeys.contains(packageName)) {
                    continue
                }

                // Check if this installed app has a verified web domain
                val dynamicWeb = WebAppCatalog.discoverDynamicWebDomain(context, packageName)
                if (dynamicWeb != null) {
                    val appName = resolveInfo.loadLabel(packageManager).toString()
                    val icon = try {
                        resolveInfo.loadIcon(packageManager)
                    } catch (e: Exception) {
                        WebIconHelper.getIconForApp(context, appName, packageName)
                    }

                    apps.add(
                        AppInfo(
                            packageName = packageName,
                            appName = appName,
                            icon = icon,
                            isSelected = selectedPackages.contains(packageName),
                            isSystemApp = false,
                            hasWebVersion = true
                        )
                    )
                    seenKeys.add(packageName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort: Selected apps first, then alphabetical by name
        apps.sortWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
        apps
    }

    private fun findActivePackage(curated: CuratedWebApp): String {
        val allPackages = listOf(curated.id) + curated.alternativePackages
        for (pkg in allPackages) {
            if (isPackageInstalled(pkg)) {
                return pkg
            }
        }
        return curated.id
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getSelectedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val allApps = getInstalledApps()
        val selectedSet = preferencesManager.getSelectedPackages()
        val filtered = allApps.filter {
            selectedSet.contains(it.packageName) ||
                    WebAppCatalog.resolveWebApp(context, it.packageName) != null &&
                    selectedSet.any { sel -> it.packageName == sel }
        }

        if (filtered.isNotEmpty()) {
            filtered
        } else {
            // Safe fallback if previous selections were native apps that are now excluded
            val defaultPkgs = PreferencesManager.DEFAULT_PACKAGES
            allApps.filter { defaultPkgs.contains(it.packageName) }
        }
    }
}
