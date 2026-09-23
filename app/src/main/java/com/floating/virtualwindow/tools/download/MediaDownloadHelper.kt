package com.floating.virtualwindow.tools.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast

object MediaDownloadHelper {

    fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") ||
                lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
                lower.endsWith(".mov") || lower.endsWith(".mkv") ||
                lower.endsWith(".wav") || lower.contains(".mp4?") ||
                lower.contains(".webm?") || lower.contains(".mp3?")
    }

    fun downloadMedia(context: Context, mediaUrl: String, pageTitle: String?) {
        try {
            val uri = Uri.parse(mediaUrl)
            var fileName = URLUtil.guessFileName(mediaUrl, null, null)

            // If guess gives generic name, use clean page title
            if ((fileName == "downloadfile.bin" || fileName.startsWith("videoplayback")) && !pageTitle.isNullOrBlank()) {
                val cleanTitle = pageTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
                val ext = if (mediaUrl.contains(".mp3")) ".mp3" else ".mp4"
                fileName = "${cleanTitle}$ext"
            }

            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading from Orbis")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
