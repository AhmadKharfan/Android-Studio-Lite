package com.ahmadkharfan.androidstudiolite.designsystem.editor

import androidx.annotation.ColorInt
import com.ahmadkharfan.androidstudiolite.domain.model.EditorColorScheme

data class EditorPalette(
    @ColorInt val canvas: Int,
    @ColorInt val gutter: Int,
    @ColorInt val lineHighlight: Int,
    @ColorInt val selection: Int,
    @ColorInt val cursor: Int,
    @ColorInt val divider: Int,
    @ColorInt val gutterTextActive: Int,
    @ColorInt val gutterTextInactive: Int,
    @ColorInt val breakpoint: Int,
    @ColorInt val defaultText: Int,
    @ColorInt val keyword: Int,
    @ColorInt val string: Int,
    @ColorInt val comment: Int,
    @ColorInt val function: Int,
    @ColorInt val type: Int,
    @ColorInt val variable: Int,
    @ColorInt val number: Int,
    @ColorInt val findMatch: Int,
    @ColorInt val findCurrent: Int,
    @ColorInt val bracketMatch: Int,
) {
    companion object {
        fun forScheme(schemeId: String): EditorPalette = when (schemeId) {
            EditorColorScheme.LIGHT -> GithubLight
            EditorColorScheme.HIGH_CONTRAST -> HighContrast
            else -> Darcula
        }

        fun isDarkScheme(schemeId: String): Boolean = EditorColorScheme.isDark(schemeId)

        private val Darcula = EditorPalette(
            0xFF1E1E1E.toInt(), 0xFF1E1E1E.toInt(), 0xFF26282A.toInt(), 0xFF214283.toInt(),
            0xFFBBBBBB.toInt(), 0xFF313131.toInt(), 0xFFA1A1A1.toInt(), 0xFF606366.toInt(),
            0xFFDB5860.toInt(), 0xFFA9B7C6.toInt(), 0xFFCC7832.toInt(), 0xFF6A8759.toInt(),
            0xFF808080.toInt(), 0xFFFFC66D.toInt(), 0xFF4EC9B0.toInt(), 0xFFA9B7C6.toInt(),
            0xFF6897BB.toInt(), 0x3FFBBF24.toInt(), 0x6634D399.toInt(), 0xFF34D399.toInt(),
        )
        private val GithubLight = EditorPalette(
            0xFFFFFFFF.toInt(), 0xFFF7F8FA.toInt(), 0xFFF2F6F4.toInt(), 0xFFB4D7FF.toInt(),
            0xFF1E1E2E.toInt(), 0xFFE4E5E9.toInt(), 0xFF5F6371.toInt(), 0xFF9A9DAB.toInt(),
            0xFFEF4444.toInt(), 0xFF24292F.toInt(), 0xFFCF222E.toInt(), 0xFF0A3069.toInt(),
            0xFF6E7781.toInt(), 0xFF8250DF.toInt(), 0xFF953800.toInt(), 0xFF24292F.toInt(),
            0xFF0550AE.toInt(), 0x3FF59E0B.toInt(), 0x6610B981.toInt(), 0xFF10B981.toInt(),
        )
        private val HighContrast = EditorPalette(
            0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF1A1A1A.toInt(), 0xFF2D5A88.toInt(),
            0xFFFFFFFF.toInt(), 0xFF3A3A3A.toInt(), 0xFFFFFFFF.toInt(), 0xFFBBBBBB.toInt(),
            0xFFFF6B6B.toInt(), 0xFFFFFFFF.toInt(), 0xFF56B6C2.toInt(), 0xFF98C379.toInt(),
            0xFF7F848E.toInt(), 0xFFE5C07B.toInt(), 0xFF61AFEF.toInt(), 0xFFFFFFFF.toInt(),
            0xFFD19A66.toInt(), 0x40FFFF00.toInt(), 0x6634D399.toInt(), 0xFF34D399.toInt(),
        )
    }
}
