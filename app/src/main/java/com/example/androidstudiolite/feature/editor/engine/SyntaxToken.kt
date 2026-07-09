package com.example.androidstudiolite.feature.editor.engine
import com.example.androidstudiolite.designsystem.component.content.AslSyntaxColor
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
fun TokenType.toAslSyntaxColor(): AslSyntaxColor? = when (this) {
    TokenType.Keyword -> AslSyntaxColor.Keyword
    TokenType.StringLiteral -> AslSyntaxColor.StringLiteral
    TokenType.Comment -> AslSyntaxColor.Comment
    TokenType.Number -> AslSyntaxColor.Number
    TokenType.Type -> AslSyntaxColor.Type
    TokenType.Function -> AslSyntaxColor.Function
    TokenType.Variable -> AslSyntaxColor.Variable
    TokenType.Annotation -> AslSyntaxColor.Function
    TokenType.Plain -> null
}
