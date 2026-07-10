package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class FunctionBodyCompletionTest {
    private val controller = EditorCompletionController(lspEnabled = { true })
    @Test
    fun emptyFunctionBody_doesNotOfferKeywords() {
        val code = "fun demo() {\n    |}"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertFalse("if" in labels)
        assertFalse("for" in labels)
        assertFalse("val" in labels)
    }
    @Test
    fun composeCall_doesNotOfferKeywordsOnOpenParen() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertFalse("if" in labels)
        assertFalse("for" in labels)
        assertTrue("text =" in labels)
    }
}
