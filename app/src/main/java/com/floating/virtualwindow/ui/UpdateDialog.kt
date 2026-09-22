package com.floating.virtualwindow.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.floating.virtualwindow.R
import com.floating.virtualwindow.updater.UpdateInfo
import com.floating.virtualwindow.updater.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min

class UpdateDialog(
    private val context: Context,
    private val updateInfo: UpdateInfo,
    private val updateManager: UpdateManager
) {

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_FloatingVirtualWindow)
    private val dialog: Dialog = Dialog(themedContext, R.style.Theme_FloatingVirtualWindow)
    private var downloadedApk: File? = null
    private var isDownloading = false

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val view = LayoutInflater.from(themedContext).inflate(R.layout.dialog_update, null)
        dialog.setContentView(view)

        val tvUpdateVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvUpdateSize = view.findViewById<TextView>(R.id.tvUpdateSize)
        val tvUpdateNotes = view.findViewById<TextView>(R.id.tvUpdateNotes)
        val llDownloadProgress = view.findViewById<LinearLayout>(R.id.llDownloadProgress)
        val pbDownload = view.findViewById<ProgressBar>(R.id.pbDownload)
        val tvDownloadStatus = view.findViewById<TextView>(R.id.tvDownloadStatus)
        val btnUpdateCancel = view.findViewById<Button>(R.id.btnUpdateCancel)
        val btnUpdateAction = view.findViewById<Button>(R.id.btnUpdateAction)

        val currentVersion = updateManager.getCurrentVersionName()
        tvUpdateVersion.text = "v$currentVersion → v${updateInfo.versionName}"
        tvUpdateSize.text = updateInfo.apkSize
        tvUpdateNotes.text = updateInfo.releaseNotes

        btnUpdateAction.text = "Download & Update (${updateInfo.apkSize})"

        if (updateInfo.forceUpdate) {
            btnUpdateCancel.visibility = View.GONE
            dialog.setCancelable(false)
        } else {
            btnUpdateCancel.setOnClickListener {
                if (!isDownloading) {
                    dialog.dismiss()
                }
            }
        }

        btnUpdateAction.setOnClickListener {
            if (downloadedApk != null) {
                // Already downloaded, install now
                val launched = updateManager.installApk(downloadedApk!!)
                if (launched) {
                    dialog.dismiss()
                }
                return@setOnClickListener
            }

            if (isDownloading) return@setOnClickListener

            // Start download
            isDownloading = true
            btnUpdateAction.isEnabled = false
            btnUpdateAction.text = "Downloading..."
            llDownloadProgress.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val apk = updateManager.downloadApk(updateInfo.apkUrl) { percent, downloaded, total ->
                    if (percent >= 0) {
                        pbDownload.isIndeterminate = false
                        pbDownload.progress = percent
                        val dlMB = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                        val totalMB = String.format("%.1f", total / (1024.0 * 1024.0))
                        tvDownloadStatus.text = "Downloading: $percent% • $dlMB MB / $totalMB MB"
                    } else {
                        pbDownload.isIndeterminate = true
                        val dlMB = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                        tvDownloadStatus.text = "Downloading: $dlMB MB..."
                    }
                }

                isDownloading = false
                if (apk != null && apk.exists()) {
                    downloadedApk = apk
                    pbDownload.progress = 100
                    tvDownloadStatus.text = "Download complete! Ready to install."
                    btnUpdateAction.isEnabled = true
                    btnUpdateAction.text = "Install Update Now"

                    // Auto-launch installer
                    updateManager.installApk(apk)
                } else {
                    btnUpdateAction.isEnabled = true
                    btnUpdateAction.text = "Retry Download"
                    tvDownloadStatus.text = "Download failed. Please check internet connection."
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun show() {
        if (!dialog.isShowing) {
            dialog.show()
            val displayMetrics = context.resources.displayMetrics
            val maxAllowedWidth = (440 * displayMetrics.density).toInt()
            val targetWidth = min((displayMetrics.widthPixels * 0.90).toInt(), maxAllowedWidth)
            dialog.window?.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
