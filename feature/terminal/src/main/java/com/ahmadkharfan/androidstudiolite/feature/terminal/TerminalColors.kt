package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.ui.graphics.Color
import com.ahmadkharfan.androidstudiolite.feature.terminal.emulator.DEFAULT_COLOR

fun ansiColor(index: Int, default: Color, lightBackground: Boolean = false): Color {
    if (index == DEFAULT_COLOR) return default
    if (index >= 256) {
        val rgb = index - 256
        return Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    }
    if (index < 16) {
        val base = ANSI_16[index]
        if (!lightBackground) return base
        return when (index) {
            7 -> Color(0xFF6B7280)
            15 -> Color(0xFF1F2937)
            else -> base
        }
    }
    if (index < 232) {
        val n = index - 16
        val r = n / 36
        val g = (n % 36) / 6
        val b = n % 6
        fun step(v: Int) = if (v == 0) 0 else 55 + v * 40
        return Color(0xFF000000.toInt() or (step(r) shl 16) or (step(g) shl 8) or step(b))
    }
    val gray = 8 + (index - 232) * 10
    return Color(0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray)
}

private val ANSI_16 = listOf(
    Color(0xFF000000), Color(0xFFCD3131), Color(0xFF0DBC79), Color(0xFFE5E510),
    Color(0xFF2472C8), Color(0xFFBC3FBC), Color(0xFF11A8CD), Color(0xFFE5E5E5),
    Color(0xFF666666), Color(0xFFF14C4C), Color(0xFF23D18B), Color(0xFFF5F543),
    Color(0xFF3B8EEA), Color(0xFFD670D6), Color(0xFF29B8DB), Color(0xFFFFFFFF),
)
