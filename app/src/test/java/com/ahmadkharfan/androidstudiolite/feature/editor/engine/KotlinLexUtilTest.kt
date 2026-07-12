package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class KotlinLexUtilTest {
    @Test
    fun insideStringLiteral_detectsRegularString() {
        val text = """val s = "hello world""""
        val caret = text.length - 1
        assertTrue(KotlinLexUtil.isInsideStringLiteral(text, caret))
    }
    @Test
    fun composableStringExit_detectsBeforeClosingQuote() {
        val text = """Text("Hello")"""
        val caret = text.indexOf('"') + 6
        assertTrue(KotlinLexUtil.isComposableStringExitPosition(text, caret))
    }
    @Test
    fun declarationBeforeParen_funKeyword() {
        val text = "fun foo("
        assertTrue(KotlinLexUtil.isDeclarationBeforeParen(text, text.indexOf("foo")))
    }
    @Test
    fun declarationBeforeParen_notForCall() {
        val text = "Image("
        assertFalse(KotlinLexUtil.isDeclarationBeforeParen(text, text.indexOf("Image")))
    }
}
