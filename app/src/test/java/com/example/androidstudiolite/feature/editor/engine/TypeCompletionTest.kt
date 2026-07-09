package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class TypeCompletionTest {
    private val controller = EditorCompletionController(lspEnabled = { true })
    @Test
    fun parameterTypePosition_offersStringType() {
        val code = "fun foo(x: |)"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.query(s).map { it.label }
        assertTrue("String" in labels)
        assertFalse("if" in labels)
    }
    @Test
    fun functionDeclarationParen_doesNotOfferCallParams() {
        val code = "fun makeUser(|)"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.query(s).map { it.label }
        assertFalse(labels.any { it.endsWith(" =") })
    }
}
