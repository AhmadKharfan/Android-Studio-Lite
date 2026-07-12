package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
class SignatureHelpTest {
    @Test
    fun textCall_showsSignatureWithActiveParameter() {
        val code = "@Composable\nfun S() { Text(|) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val help = KotlinSignatureHelpResolver.fromCallSite(text, caret)
        assertNotNull(help)
        assertEquals("Text", help!!.calleeName.substringAfterLast('.'))
        assertTrue(help.signatureLabel.contains("text: String"))
        assertTrue(help.signatureLabel.contains("modifier: Modifier"))
        assertEquals(0, help.activeParameter)
    }
    @Test
    fun secondArgument_highlightsModifierParam() {
        val code = "@Composable\nfun S() { Text(\"Hi\", |) }"
        val caret = code.indexOf('|')
        val text = code.replace("|", "")
        val help = KotlinSignatureHelpResolver.fromCallSite(text, caret)
        assertNotNull(help)
        val active = help!!.parameters[help.activeParameter]
        assertTrue(active.label.startsWith("modifier"))
    }
}
