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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object WebIconHelper {
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    private val PALETTE = intArrayOf(
        0xFF10A37F.toInt(), // ChatGPT Teal
        0xFFE1306C.toInt(), // Instagram Pink
        0xFFFF0000.toInt(), // YouTube Red
        0xFF25D366.toInt(), // WhatsApp Green
        0xFF0088CC.toInt(), // Telegram Blue
        0xFF1DA1F2.toInt(), // Twitter Blue
        0xFF1DB954.toInt(), // Spotify Green
        0xFF5865F2.toInt(), // Discord Indigo
        0xFFFF4500.toInt(), // Reddit Orange
        0xFF4285F4.toInt(), // Google Blue
        0xFFEA4335.toInt(), // Google Red
        0xFF34A853.toInt(), // Google Green
        0xFFFBBC05.toInt(), // Google Yellow
        0xFF6C5CE7.toInt(), // Purple
        0xFF0984E3.toInt(), // Ocean Blue
        0xFF00B894.toInt(), // Mint Green
        0xFFD63031.toInt(), // Coral Red
        0xFF6C5CE7.toInt()  // Indigo
    )

    fun getIconForApp(context: Context, name: String, packageName: String): Drawable {
        // 1. Try local installed app icon first if package is installed on device
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0)
            return pm.getApplicationIcon(packageName)
        } catch (_: Exception) {}

        // 2. Check memory cache
        val cached = iconCache[packageName]
        if (cached != null) return cached

        // 3. Generate clean branded squircle letter avatar
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
