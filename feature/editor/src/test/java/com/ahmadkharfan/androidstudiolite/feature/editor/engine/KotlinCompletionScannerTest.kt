package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class KotlinCompletionScannerTest {
    @Test
    fun insideStringLiteral_isSuppressed() {
        val text = """val s = "hello wor"""
        val caret = text.length
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertTrue(ctx.suppressed)
    }
    @Test
    fun insideTemplateExpression_isAllowed() {
        val text = """val s = "count = ${'$'}{cou"""
        val caret = text.length
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
        assertEquals(CompletionPositionKind.NameReference, ctx.positionKind)
    }
    @Test
    fun insideLineComment_isSuppressed() {
        val text = "val x = 1 // comm"
        val ctx = KotlinCompletionScanner.scan(text, text.length)
        assertTrue(ctx.suppressed)
    }
    @Test
    fun importLine_isImportContext() {
        val text = "import androidx.act"
        val caret = text.length
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertFalse(ctx.suppressed)
        assertTrue(ctx.importContext)
        assertEquals(CompletionPositionKind.Import, ctx.positionKind)
        assertEquals("act", ctx.prefix)
    }
    @Test
    fun memberAccess_classifiesCorrectly() {
        val text = "Modifier.pad"
        val caret = text.length
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertEquals(CompletionPositionKind.MemberAccess, ctx.positionKind)
        assertEquals("Modifier", ctx.qualifier)
    }
    @Test
    fun typePosition_afterColon() {
        val text = "val x: Str"
        val caret = text.length
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertEquals(CompletionPositionKind.TypeReference, ctx.positionKind)
    }
    @Test
    fun shouldAutoPopup_rejectsInsideString() {
        val text = """text = "hel"""
        assertFalse(KotlinCompletionScanner.shouldAutoPopup(text, text.length, 'l'))
    }
    @Test
    fun shouldAutoPopup_acceptsDot() {
        val text = "Modifier."
        assertTrue(KotlinCompletionScanner.shouldAutoPopup(text, text.length, '.'))
    }
    @Test
    fun shouldAutoPopup_acceptsOpenParenInsideCall() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        assertTrue(KotlinCompletionScanner.shouldAutoPopup(text, caret, '('))
    }
    @Test
    fun callArgument_insideTextParens() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val ctx = KotlinCompletionScanner.scan(text, caret)
        assertEquals(CompletionPositionKind.CallArgument, ctx.positionKind)
        assertEquals("", ctx.prefix)
        assertNotNull(ctx.callSite)
        assertEquals("Text", ctx.callSite!!.calleeName)
    }
    @Test
    fun blockComment_suppressesUntilClosed() {
        assertTrue(scanAtMarker("val x = 1 /* block| comment */").suppressed)
        assertTrue(scanAtMarker("val x = 1 /* unterminated|").suppressed)
        assertFalse(scanAtMarker("val x = 1 /* block */ |foo").suppressed)
    }
    @Test
    fun rawString_suppressesUntilTripleQuoteCloses() {
        assertTrue(scanAtMarker("val s = \"\"\"raw \" quote|\"\"\"").suppressed)
        assertFalse(scanAtMarker("val s = \"\"\"raw \" quote\"\"\"; |foo").suppressed)
    }
    @Test
    fun charLiteral_suppressesUntilClosingQuote() {
        assertTrue(scanAtMarker("val c = 'x|'; foo").suppressed)
        assertFalse(scanAtMarker("val c = 'x'; |foo").suppressed)
        assertTrue(scanAtMarker("val c = '\\'|'; foo").suppressed)
        assertFalse(scanAtMarker("val c = '\\''; |foo").suppressed)
    }
    @Test
    fun identifierTemplate_allowsCompletionInsideIdentifier() {
        val ctx = scanAtMarker("val s = \"\$fo|o\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun expressionTemplate_preservesNestedBraceDepth() {
        val simple = scanAtMarker("val s = \"\${fo|o}\"")
        assertFalse(simple.suppressed)
        assertTrue(simple.inTemplateExpression)
        val nested = scanAtMarker("val s = \"\${foo({ ba|r })}\"")
        assertFalse(nested.suppressed)
        assertTrue(nested.inTemplateExpression)
        assertTrue(scanAtMarker("val s = \"\${foo({ bar })}|\"").suppressed)
    }
    @Test
    fun templateExpression_resumesAfterInnerStringCloses() {
        val text = "val s = \"\${ f(\"x\")"
        val ctx = KotlinCompletionScanner.scan(text, text.length)
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun completedTemplate_doesNotSuppressFollowingCode() {
        val text = "val s = \"\${ f(\"x\") }\"\nval y ="
        val ctx = KotlinCompletionScanner.scan(text, text.length)
        assertFalse(ctx.suppressed)
        assertFalse(ctx.inTemplateExpression)
    }
    @Test
    fun templateExpression_resumesAfterLineCommentCloses() {
        val ctx = scanAtMarker("val s = \"\${ f() // comment\n|foo }\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun templateExpression_resumesAfterBlockCommentCloses() {
        val ctx = scanAtMarker("val s = \"\${ f() /* comment */|foo }\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun templateExpression_resumesAfterCharCloses() {
        val ctx = scanAtMarker("val s = \"\${ f('x')| }\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun templateExpression_resumesAfterRawStringCloses() {
        val ctx = scanAtMarker("val s = \"\${ f(\"\"\"x\"\"\")| }\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
    }
    @Test
    fun nestedTemplate_restoresEnclosingTemplateDepth() {
        val ctx = scanAtMarker("val s = \"\${ \"\${ x }\"| }\"")
        assertFalse(ctx.suppressed)
        assertTrue(ctx.inTemplateExpression)
        val completed = scanAtMarker("val s = \"\${ \"\${ x }\" }\"; |foo")
        assertFalse(completed.suppressed)
        assertFalse(completed.inTemplateExpression)
    }
    @Test
    fun escapedQuote_doesNotCloseNormalString() {
        assertTrue(scanAtMarker("val s = \"a\\\"|b\"").suppressed)
    }
    private fun scanAtMarker(markedText: String): KotlinCaretContext {
        val caret = markedText.indexOf('|')
        val text = markedText.removeRange(caret, caret + 1)
        return KotlinCompletionScanner.scan(text, caret)
    }
}
