package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class SyntaxLexerTest {
    private val kotlin = SyntaxLexer.forLanguage(EditorLanguage.Kotlin)
    private fun typeOf(line: String, tokens: List<SyntaxToken>, word: String): TokenType? =
        tokens.firstOrNull { line.substring(it.start, it.end) == word }?.type
    @Test
    fun kotlinSample_classifiesKeywordsFunctionsAndStrings() {
        val line = "    val greeting = \"Hello, Lite!\""
        val result = kotlin.tokenizeLine(line, LexerState.Default)
        assertEquals(TokenType.Keyword, typeOf(line, result.tokens, "val"))
        assertEquals(TokenType.Variable, typeOf(line, result.tokens, "greeting"))
        assertTrue(result.tokens.any { it.type == TokenType.StringLiteral && line.substring(it.start, it.end) == "\"Hello, Lite!\"" })
        assertEquals(LexerState.Default, result.endState)
    }
    @Test
    fun identifierFollowedByParen_isFunction_capitalizedIsType() {
        val line = "class MainActivity { fun onCreate() {}"
        val result = kotlin.tokenizeLine(line, LexerState.Default)
        assertEquals(TokenType.Keyword, typeOf(line, result.tokens, "class"))
        assertEquals(TokenType.Type, typeOf(line, result.tokens, "MainActivity"))
        assertEquals(TokenType.Keyword, typeOf(line, result.tokens, "fun"))
        assertEquals(TokenType.Function, typeOf(line, result.tokens, "onCreate"))
    }
    @Test
    fun lineComment_coversRestOfLine() {
        val line = "val x = 1 // trailing"
        val result = kotlin.tokenizeLine(line, LexerState.Default)
        val comment = result.tokens.single { it.type == TokenType.Comment }
        assertEquals("// trailing", line.substring(comment.start, comment.end))
    }
    @Test
    fun blockComment_carriesStateAcrossLines() {
        val open = kotlin.tokenizeLine("code /* start", LexerState.Default)
        assertEquals(LexerState.BlockComment, open.endState)
        val close = kotlin.tokenizeLine("still */ code", LexerState.BlockComment)
        assertEquals(LexerState.Default, close.endState)
        assertEquals(TokenType.Comment, close.tokens.first().type)
        assertEquals("still */", "still */ code".substring(close.tokens.first().start, close.tokens.first().end))
    }
    @Test
    fun kotlinRawString_carriesStateAcrossLines() {
        val open = kotlin.tokenizeLine("val s = \"\"\"line one", LexerState.Default)
        assertEquals(LexerState.RawString, open.endState)
        val close = kotlin.tokenizeLine("line two\"\"\"", LexerState.RawString)
        assertEquals(LexerState.Default, close.endState)
    }
    @Test
    fun xmlLexer_classifiesTagAttributesAndValues() {
        val xml = SyntaxLexer.forLanguage(EditorLanguage.Xml)
        val line = "<activity android:name=\".MainActivity\">"
        val result = xml.tokenizeLine(line, LexerState.Default)
        assertEquals(TokenType.Type, typeOf(line, result.tokens, "activity"))
        assertTrue(result.tokens.any { it.type == TokenType.Variable && line.substring(it.start, it.end) == "android:name" })
        assertTrue(result.tokens.any { it.type == TokenType.StringLiteral && line.substring(it.start, it.end) == "\".MainActivity\"" })
    }
}
