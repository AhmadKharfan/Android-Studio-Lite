package com.ahmadkharfan.androidstudiolite.feature.editor.engine
enum class TokenType {
    Plain,
    Keyword,
    StringLiteral,
    Comment,
    Number,
    Type,
    Function,
    Variable,
    Annotation,
}
data class SyntaxToken(val start: Int, val end: Int, val type: TokenType)
