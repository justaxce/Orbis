package com.floating.virtualwindow.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.floating.virtualwindow.engine.GuestLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "UpdateManager"
        const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/justaxce/Orbis/main/version.json"
        const val GITHUB_RELEASES_LATEST_API = "https://api.github.com/repos/justaxce/Orbis/releases/latest"
    }

    suspend fun checkForUpdate(versionUrl: String = DEFAULT_VERSION_URL): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            // Strategy 1: Check version.json with cache busting
            val updateFromJson = checkViaVersionJson(versionUrl)
            if (updateFromJson != null) {
                Log.d(TAG, "Update found via version.json: ${updateFromJson.versionName}")
                return@withContext updateFromJson
            }

            // Strategy 2: Direct GitHub Releases API fallback (bypasses Fastly CDN 300s cache)
            val updateFromApi = checkViaGitHubApi()
            if (updateFromApi != null) {
                Log.d(TAG, "Update found via GitHub Releases API: ${updateFromApi.versionName}")
                return@withContext updateFromApi
            }

            Log.d(TAG, "No updates available. Current version is latest.")
            null
        }
    }

    private fun checkViaVersionJson(versionUrl: String): UpdateInfo? {
        try {
            // Append timestamp to bypass GitHub Fastly CDN 5-minute cache
            val cacheBustedUrl = if (versionUrl.contains("?")) {
                "$versionUrl&_t=${System.currentTimeMillis()}"
            } else {
                "$versionUrl?_t=${System.currentTimeMillis()}"
            }

            val url = URL(cacheBustedUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                defaultUseCaches = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Orbis-Updater")
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("Expires", "0")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val rawResponse = conn.inputStream.bufferedReader().use { it.readText() }
                // Sanitize any unescaped raw newlines inside string literals
                val sanitizedJson = sanitizeJsonString(rawResponse)
                val json = JSONObject(sanitizedJson)

                val remoteCode = json.optInt("versionCode", 1)
                val remoteName = json.optString("versionName", "1.0.0")
                val apkUrl = json.optString("apkUrl", "")
                val apkSize = json.optString("apkSize", "1.9 MB")
                val releaseNotes = json.optString("releaseNotes", "Performance improvements and bug fixes.")
                val forceUpdate = json.optBoolean("forceUpdate", false)

                val localCode = getCurrentVersionCode()
                val localName = getCurrentVersionName()

                val isNewer = remoteCode > localCode || isNewerVersion(remoteName, localName)

                if (isNewer && apkUrl.isNotEmpty()) {
                    return UpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        apkUrl = apkUrl,
                        apkSize = apkSize,
                        releaseNotes = releaseNotes,
                        forceUpdate = forceUpdate
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "version.json check failed: ${e.message}")
        }
        return null
    }

    private fun checkViaGitHubApi(): UpdateInfo? {
        try {
            val url = URL(GITHUB_RELEASES_LATEST_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Orbis-Updater")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Cache-Control", "no-cache")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "") // e.g. "v1.0.8"
                val remoteName = tagName.removePrefix("v").trim()
                val releaseNotes = json.optString("body", "What's new:\n• Bug fixes and stability improvements.")

                // Look for Orbis.apk in assets
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkSizeBytes = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.equals("Orbis.apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            apkSizeBytes = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                val apkSizeStr = if (apkSizeBytes > 0) {
                    String.format("%.1f MB", apkSizeBytes / (1024.0 * 1024.0))
                } else {
                    "1.9 MB"
                }

                val localName = getCurrentVersionName()
                if (apkUrl.isNotEmpty() && isNewerVersion(remoteName, localName)) {
                    val localCode = getCurrentVersionCode().toInt()
                    return UpdateInfo(
                        versionCode = localCode + 1,
                        versionName = remoteName,
                        apkUrl = apkUrl,
                        apkSize = apkSizeStr,
                        releaseNotes = releaseNotes,
                        forceUpdate = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API release check failed: ${e.message}")
        }
        return null
    }

    /**
     * Compares semantic version strings e.g. "1.0.8" vs "1.0.7"
     */
    fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        try {
            val cleanRemote = remoteVersion.removePrefix("v").trim()
            val cleanLocal = localVersion.removePrefix("v").trim()
            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (e: Exception) {
            return remoteVersion.compareTo(localVersion) > 0
        }
        return false
    }

    /**
     * Sanitizes raw unescaped newlines inside JSON string values.
     */
    private fun sanitizeJsonString(raw: String): String {
        val sb = java.lang.StringBuilder(raw.length + 32)
        var inString = false
        var isEscaped = false
        for (char in raw) {
            if (char == '\\' && !isEscaped) {
                isEscaped = true
                sb.append(char)
                continue
            }
            if (char == '"' && !isEscaped) {
                inString = !inString
            }
            if (inString && (char == '\n' || char == '\r') && !isEscaped) {
                if (char == '\n') sb.append("\\n")
            } else {
                sb.append(char)
            }
            isEscaped = false
        }
        return sb.toString()
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Follow redirects in a loop (e.g. github.com -> objects.githubusercontent.com)
                var currentUrl = apkUrl
                var conn: HttpURLConnection? = null
                var redirectCount = 0

                while (redirectCount < 5) {
                    val url = URL(currentUrl)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Orbis-Updater")
                    }

                    val code = conn.responseCode
                    if (code in 300..399) {
                        val newLocation = conn.getHeaderField("Location")
                        if (!newLocation.isNullOrEmpty()) {
                            conn.disconnect()
                            currentUrl = newLocation
                            redirectCount++
                            continue
                        }
                    }
                    break
                }

                val finalConn = conn ?: return@withContext null
                val totalBytes = finalConn.contentLength.toLong()
                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val targetFile = File(updatesDir, "Orbis_update.apk")
                if (targetFile.exists()) targetFile.delete()

                val input: InputStream = finalConn.inputStream
                val output = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalDownloaded: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalDownloaded += bytesRead

                    val percent = if (totalBytes > 0) {
                        ((totalDownloaded * 100) / totalBytes).toInt()
                    } else {
                        -1
                    }

                    mainHandler.post {
                        onProgress(percent, totalDownloaded, totalBytes)
                    }
                }

                output.flush()
                output.close()
                input.close()
                finalConn.disconnect()

                return@withContext targetFile
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK", e)
                null
            }
        }
    }

    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        // Attempt silent install via Shizuku if active and permitted
        if (GuestLauncher.isShizukuAvailable()) {
            try {
                val cmd = "pm install -r \"${apkFile.absolutePath}\""
                val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
                process?.waitFor()
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Standard Android PackageInstaller intent via FileProvider
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
