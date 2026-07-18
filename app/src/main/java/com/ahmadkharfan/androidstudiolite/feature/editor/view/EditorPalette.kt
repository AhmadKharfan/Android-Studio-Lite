package com.ahmadkharfan.androidstudiolite.feature.editor.view
import androidx.annotation.ColorInt
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.TokenType
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
    @ColorInt
    fun colorFor(tokenType: TokenType): Int = when (tokenType) {
        TokenType.Keyword -> keyword
        TokenType.StringLiteral -> string
        TokenType.Comment -> comment
        TokenType.Function -> function
        TokenType.Type -> type
        TokenType.Variable -> variable
        TokenType.Number -> number
        TokenType.Annotation -> function
        TokenType.Plain -> defaultText
    }

    companion object {
        /**
         * The self-contained editor color scheme selected in Editor settings. Independent of the app's
         * light/dark UI theme: the code surface keeps the chosen scheme's canvas + syntax colors.
         */
        fun forScheme(schemeId: String): EditorPalette = when (schemeId) {
            "light" -> GithubLight
            "hc" -> HighContrast
            else -> Darcula
        }

        private val Darcula = EditorPalette(
            canvas = 0xFF1E1E1E.toInt(),
            gutter = 0xFF1E1E1E.toInt(),
            lineHighlight = 0xFF26282A.toInt(),
            selection = 0xFF214283.toInt(),
            cursor = 0xFFBBBBBB.toInt(),
            divider = 0xFF313131.toInt(),
            gutterTextActive = 0xFFA1A1A1.toInt(),
            gutterTextInactive = 0xFF606366.toInt(),
            breakpoint = 0xFFDB5860.toInt(),
            defaultText = 0xFFA9B7C6.toInt(),
            keyword = 0xFFCC7832.toInt(),
            string = 0xFF6A8759.toInt(),
            comment = 0xFF808080.toInt(),
            function = 0xFFFFC66D.toInt(),
            type = 0xFF4EC9B0.toInt(),
            variable = 0xFFA9B7C6.toInt(),
            number = 0xFF6897BB.toInt(),
            findMatch = 0x3FFBBF24.toInt(),
            findCurrent = 0x6634D399.toInt(),
            bracketMatch = 0xFF34D399.toInt(),
        )

        private val GithubLight = EditorPalette(
            canvas = 0xFFFFFFFF.toInt(),
            gutter = 0xFFF7F8FA.toInt(),
            lineHighlight = 0xFFF2F6F4.toInt(),
            selection = 0xFFB4D7FF.toInt(),
            cursor = 0xFF1E1E2E.toInt(),
            divider = 0xFFE4E5E9.toInt(),
            gutterTextActive = 0xFF5F6371.toInt(),
            gutterTextInactive = 0xFF9A9DAB.toInt(),
            breakpoint = 0xFFEF4444.toInt(),
            defaultText = 0xFF24292F.toInt(),
            keyword = 0xFFCF222E.toInt(),
            string = 0xFF0A3069.toInt(),
            comment = 0xFF6E7781.toInt(),
            function = 0xFF8250DF.toInt(),
            type = 0xFF953800.toInt(),
            variable = 0xFF24292F.toInt(),
            number = 0xFF0550AE.toInt(),
            findMatch = 0x3FF59E0B.toInt(),
            findCurrent = 0x6610B981.toInt(),
            bracketMatch = 0xFF10B981.toInt(),
        )

        private val HighContrast = EditorPalette(
            canvas = 0xFF000000.toInt(),
            gutter = 0xFF000000.toInt(),
            lineHighlight = 0xFF1A1A1A.toInt(),
            selection = 0xFF2D5A88.toInt(),
            cursor = 0xFFFFFFFF.toInt(),
            divider = 0xFF3A3A3A.toInt(),
            gutterTextActive = 0xFFFFFFFF.toInt(),
            gutterTextInactive = 0xFFBBBBBB.toInt(),
            breakpoint = 0xFFFF6B6B.toInt(),
            defaultText = 0xFFFFFFFF.toInt(),
            keyword = 0xFF56B6C2.toInt(),
            string = 0xFF98C379.toInt(),
            comment = 0xFF7F848E.toInt(),
            function = 0xFFE5C07B.toInt(),
            type = 0xFF61AFEF.toInt(),
            variable = 0xFFFFFFFF.toInt(),
            number = 0xFFD19A66.toInt(),
            findMatch = 0x40FFFF00.toInt(),
            findCurrent = 0x6634D399.toInt(),
            bracketMatch = 0xFF34D399.toInt(),
        )
    }
}
