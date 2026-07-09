package com.example.androidstudiolite.feature.editor.view
import androidx.annotation.ColorInt
import com.example.androidstudiolite.feature.editor.engine.TokenType
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
    @ColorInt val diagnosticError: Int,
    @ColorInt val diagnosticWarning: Int,
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
}
