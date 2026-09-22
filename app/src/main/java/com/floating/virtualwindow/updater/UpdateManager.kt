package com.floating.virtualwindow.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
        const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/justaxce/Orbis/main/version.json"
    }

    suspend fun checkForUpdate(versionUrl: String = DEFAULT_VERSION_URL): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(versionUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Orbis-Updater")
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)

                    val remoteCode = json.optInt("versionCode", 1)
                    val remoteName = json.optString("versionName", "1.0.0")
                    val apkUrl = json.optString("apkUrl", "")
                    val apkSize = json.optString("apkSize", "1.8 MB")
                    val releaseNotes = json.optString("releaseNotes", "Performance improvements and bug fixes.")
                    val forceUpdate = json.optBoolean("forceUpdate", false)

                    val localCode = getCurrentVersionCode()

                    if (remoteCode > localCode && apkUrl.isNotEmpty()) {
                        return@withContext UpdateInfo(
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
                e.printStackTrace()
            }
            null
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 15000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Orbis-Updater")
                }

                val totalBytes = conn.contentLength.toLong()
                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val targetFile = File(updatesDir, "Orbis_update.apk")
                if (targetFile.exists()) targetFile.delete()

                val input: InputStream = conn.inputStream
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

                return@withContext targetFile
            } catch (e: Exception) {
                e.printStackTrace()
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
