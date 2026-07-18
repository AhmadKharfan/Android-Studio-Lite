package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class CallArgumentCompletionTest {
    private val controller = EditorCompletionController()
    @Test
    fun textCall_offersAllNamedParametersAtOpenParen() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("text =" in labels)
        assertTrue("modifier =" in labels)
        assertTrue("color =" in labels)
    }
    @Test
    fun textCall_offersNamedTextParameter() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("text =" in labels)
    }
    @Test
    fun namedArg_insertsNameEquals() {
        val code = "fun f() { User(na|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val item = controller.queryHeuristic(s).firstOrNull { it.label == "name =" }
        assertNotNull(item)
        assertTrue(item!!.insertText == "name = ")
    }
    @Test
    fun suppliedNamedArg_notReoffered() {
        val code = """fun f() { User(name = "x", ag|) }"""
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("age =" in labels)
        assertFalse("name =" in labels)
    }
    @Test
    fun callArgument_excludesStatementKeywords() {
        val code = "fun box(w: Int) {}\nfun g() { box(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertFalse("val" in labels)
        assertFalse("for" in labels)
        assertFalse("if" in labels)
        assertTrue("w =" in labels)
    }
    @Test
    fun colorParameter_offersColorConstants() {
        val code = "@Composable\nfun S() { Text(text = \"Hi\", color = |) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue(labels.any { it.startsWith("Color.") })
    }
    @Test
    fun columnCall_modifierParam_offersModifierMembers() {
        val code = "@Composable\nfun S() { Column(modifier = |) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue(labels.any { it == "padding" || it == "fillMaxSize" })
    }
}
