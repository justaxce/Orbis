package com.floating.virtualwindow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object WebIconHelper {
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val PALETTE = intArrayOf(
        0xFF10A37F.toInt(), 0xFFE1306C.toInt(), 0xFFFF0000.toInt(),
        0xFF25D366.toInt(), 0xFF0088CC.toInt(), 0xFF1DA1F2.toInt(),
        0xFF1DB954.toInt(), 0xFF5865F2.toInt(), 0xFFFF4500.toInt(),
        0xFF4285F4.toInt(), 0xFFEA4335.toInt(), 0xFF34A853.toInt(),
        0xFFFBBC05.toInt(), 0xFF6C5CE7.toInt(), 0xFF0984E3.toInt(),
        0xFF00B894.toInt(), 0xFFD63031.toInt()
    )

    fun getIconForApp(context: Context, name: String, packageName: String): Drawable {
        // 1. Check in-memory cache first
        val cached = iconCache[packageName]
        if (cached != null) return cached

        // 2. Try local installed app icon first if package is installed on device
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0)
            val installedIcon = pm.getApplicationIcon(packageName)
            iconCache[packageName] = installedIcon
            return installedIcon
        } catch (_: Exception) {}

        // 3. Check bundled official high-res brand icon from WebAppCatalog
        val curated = WebAppCatalog.findCuratedApp(packageName)
        if (curated != null && curated.iconRes != 0) {
            try {
                val drawable = ContextCompat.getDrawable(context, curated.iconRes)
                if (drawable != null) {
                    iconCache[packageName] = drawable
                    return drawable
                }
            } catch (_: Exception) {}
        }

        // 4. Generate clean branded squircle letter avatar as fallback
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colorIndex = abs(packageName.hashCode()) % PALETTE.size
        val bgColor = PALETTE[colorIndex]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        val cornerRadius = size * 0.28f
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, bgPaint)

        val letter = (name.firstOrNull()?.uppercaseChar() ?: 'W').toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.52f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val bounds = Rect()
        textPaint.getTextBounds(letter, 0, letter.length, bounds)
        val y = (size / 2f) + (bounds.height() / 2f) - bounds.bottom
        canvas.drawText(letter, size / 2f, y, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        iconCache[packageName] = drawable
        return drawable
    }
}
