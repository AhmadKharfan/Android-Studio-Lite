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
}
