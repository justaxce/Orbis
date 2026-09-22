package com.floating.virtualwindow.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.floating.virtualwindow.R

class WirelessGuideDialog(context: Context) : Dialog(
    ContextThemeWrapper(context, R.style.Theme_FloatingVirtualWindow),
    R.style.Theme_FloatingVirtualWindow
) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (!isActivityContext(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                @Suppress("DEPRECATION")
                window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
        }
    }

    private fun isActivityContext(ctx: Context): Boolean {
        var current: Context? = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return true
            current = current.baseContext
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_wireless_guide)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        findViewById<Button>(R.id.btnOpenAboutPhone)?.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                dismiss()
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    dismiss()
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnOpenDevOptions)?.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Developer options not enabled yet. Please tap Build Number first.", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btnCloseGuide)?.setOnClickListener {
            dismiss()
        }
    }
}
