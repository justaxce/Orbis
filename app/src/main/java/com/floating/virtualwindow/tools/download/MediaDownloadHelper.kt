package com.floating.virtualwindow.tools.download

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaDownloadHelper {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                lower.endsWith(".mov") || lower.endsWith(".mkv") ||
                lower.endsWith(".wav") || lower.contains(".mp4?") ||
                lower.contains(".webm?") || lower.contains(".mp3?")
    }

    /**
     * Cleans query parameters (e.g. bytestart, byteend, range, sq) that CDN streaming uses,
     * restoring the full video file request URL.
     */
    fun cleanMediaUrl(rawUrl: String): String {
        return try {
            val uri = Uri.parse(rawUrl)
            val builder = uri.buildUpon().clearQuery()
            for (param in uri.queryParameterNames) {
                if (param.equals("bytestart", ignoreCase = true) ||
                    param.equals("byteend", ignoreCase = true) ||
                    param.equals("range", ignoreCase = true) ||
                    param.equals("sq", ignoreCase = true)
                ) {
                    continue
                }
                for (value in uri.getQueryParameters(param)) {
                    builder.appendQueryParameter(param, value)
                }
            }
            builder.build().toString()
        } catch (e: Exception) {
            rawUrl
        }
    }

    fun downloadMedia(
        context: Context,
        rawMediaUrl: String,
        pageTitle: String?,
        webUserAgent: String? = null,
        pageUrl: String? = null
    ) {
        val mediaUrl = cleanMediaUrl(rawMediaUrl)
        val userAgent = webUserAgent ?: try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        val cookie = try {
            CookieManager.getInstance().getCookie(mediaUrl) ?: CookieManager.getInstance().getCookie(pageUrl)
        } catch (e: Exception) {
            null
        }

        val referer = pageUrl ?: "https://www.instagram.com/"

        var fileName = URLUtil.guessFileName(mediaUrl, null, null)
        val isAudio = mediaUrl.contains(".mp3", ignoreCase = true) || mediaUrl.contains(".m4a", ignoreCase = true)
        val defaultExt = if (isAudio) ".mp3" else ".mp4"

        if (fileName.contains(".bin", ignoreCase = true) || fileName.startsWith("videoplayback") || fileName.length < 5) {
            val cleanTitle = if (!pageTitle.isNullOrBlank()) {
                pageTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            } else {
                "Media_${System.currentTimeMillis()}"
            }
            fileName = "$cleanTitle$defaultExt"
        }

        if (!fileName.endsWith(".mp4", ignoreCase = true) && !fileName.endsWith(".mp3", ignoreCase = true) && !fileName.endsWith(".webm", ignoreCase = true)) {
            fileName = "$fileName$defaultExt"
        }

        Toast.makeText(context, "Starting download: $fileName", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            val success = tryDirectDownload(context, mediaUrl, fileName, userAgent, cookie, referer, isAudio)
            if (!success) {
                withContext(Dispatchers.Main) {
                    tryDownloadManager(context, mediaUrl, fileName, userAgent, cookie, referer, isAudio)
                }
            }
        }
    }

    private fun tryDirectDownload(
        context: Context,
        mediaUrl: String,
        fileName: String,
        userAgent: String,
        cookie: String?,
        referer: String,
        isAudio: Boolean
    ): Boolean {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: java.io.OutputStream? = null

        try {
            var currentUrl = mediaUrl
            var redirects = 0
            while (redirects < 5) {
                val urlObj = URL(currentUrl)
                connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", userAgent)
                    if (!cookie.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookie)
                    }
                    setRequestProperty("Referer", referer)
                    setRequestProperty("Accept", "*/*")
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val newLocation = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    currentUrl = if (newLocation.startsWith("http")) newLocation else URL(urlObj, newLocation).toString()
                    redirects++
                } else {
                    break
                }
            }

            val responseCode = connection?.responseCode ?: -1
            if (responseCode !in 200..299) {
                return false
            }

            val contentType = connection?.contentType?.lowercase() ?: ""
            if (contentType.startsWith("text/html") || contentType.startsWith("application/json")) {
                // Not a valid media stream (e.g. CDN error page)
                return false
            }

            val mimeType = if (isAudio) "audio/mp3" else "video/mp4"

            var targetPath: String? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val itemUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false
                outputStream = resolver.openOutputStream(itemUri) ?: return false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                targetPath = targetFile.absolutePath
                outputStream = FileOutputStream(targetFile)
            }

            inputStream = connection?.inputStream ?: return false
            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalDownloaded = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead
            }
            outputStream.flush()

            if (totalDownloaded < 1024) {
                // File too small to be valid media
                return false
            }

            if (targetPath != null) {
                MediaScannerConnection.scanFile(context, arrayOf(targetPath), arrayOf(mimeType), null)
            }

            mainHandler.post {
                Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { connection?.disconnect() } catch (ignored: Exception) {}
        }
    }

    private fun tryDownloadManager(
        context: Context,
        mediaUrl: String,
        fileName: String,
        userAgent: String,
        cookie: String?,
        referer: String,
        isAudio: Boolean
    ) {
        try {
            val uri = Uri.parse(mediaUrl)
            val mimeType = if (isAudio) "audio/mp3" else "video/mp4"

            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading media")
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                if (!cookie.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookie)
                }
                addRequestHeader("Referer", referer)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
