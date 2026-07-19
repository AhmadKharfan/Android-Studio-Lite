package com.ahmadkharfan.androidstudiolite.feature.editor.assets

import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import java.io.File

object AssetPreview {

    fun decodeThumbnail(path: String, maxSidePx: Int = 128): android.graphics.Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun readText(path: String, maxChars: Int = 24_000): String? {
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        if (file.length() > 512 * 1024) return "(File too large to preview)"
        return runCatching { file.readText().take(maxChars) }.getOrNull()
    }

    fun xmlDrawableTint(path: String): androidx.compose.ui.graphics.Color? {
        val text = readText(path, maxChars = 4_096) ?: return null
        val hex = Regex("""(?:android:fillColor|android:color|fillColor|color)="#([0-9A-Fa-f]{6,8})"""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?: return null
        return runCatching {
            androidx.compose.ui.graphics.Color(AndroidColor.parseColor("#$hex"))
        }.getOrNull()
    }

    fun loadTypeface(path: String): Typeface? =
        runCatching { Typeface.createFromFile(path) }.getOrNull()

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        var halfW = width / 2
        var halfH = height / 2
        while (halfW / inSampleSize >= maxSide && halfH / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
