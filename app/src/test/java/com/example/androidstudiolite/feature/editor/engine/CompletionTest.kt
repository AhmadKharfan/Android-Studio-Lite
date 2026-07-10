package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class CompletionTest {
    private val controller = EditorCompletionController()
    @Test
    fun buildContext_extractsPrefixUnderCaret() {
        val s = EditorSession("val x = ab", EditorLanguage.Kotlin).also { it.setCaret(10) }
        val ctx = controller.buildContext(s)
        assertEquals("ab", ctx.prefix)
        assertEquals(8, ctx.prefixStart)
    }
    @Test
    fun buildContext_detectsImportMemberAccess() {
        val s = EditorSession("import android.", EditorLanguage.Kotlin).also { it.setCaret(15) }
        val ctx = controller.buildContext(s)
        assertTrue(ctx.importContext)
        assertTrue(ctx.memberAccess)
        assertEquals("android", ctx.qualifier)
    }
    @Test
    fun query_insideStringLiteral_returnsEmpty() {
        val text = """val s = "hello wor"""
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(text.length) }
        assertTrue(controller.queryHeuristic(s).isEmpty())
    }
    @Test
    fun query_importAndroidDot_suggestsSubpackagesNotKeywords() {
        val s = EditorSession("import android.", EditorLanguage.Kotlin).also { it.setCaret(15) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("app" in labels)
        assertTrue("content" in labels)
        assertFalse("if" in labels)
        assertFalse("for" in labels)
    }
    @Test
    fun query_importAndroidxComposeDot_suggestsComposeChildren() {
        val s = EditorSession("import androidx.compose.", EditorLanguage.Kotlin).also { it.setCaret(24) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("foundation" in labels)
        assertTrue("material3" in labels)
        assertTrue("runtime" in labels)
    }
    @Test
    fun query_composeFile_suggestsTextAndImage() {
        val s = EditorSession(
            "@Composable\nfun Screen() {\n    Te",
            EditorLanguage.Kotlin,
        ).also { it.setCaret(32) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("Text" in labels)
    }
    @Test
    fun query_modifierDot_suggestsLayoutModifiers() {
        val s = EditorSession("Modifier.", EditorLanguage.Kotlin).also { it.setCaret(9) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("padding" in labels)
        assertTrue("fillMaxSize" in labels)
        assertFalse("if" in labels)
    }
    @Test
    fun query_offersMatchingKeywordAndSnippet() {
        val s = EditorSession("fu", EditorLanguage.Kotlin).also { it.setCaret(2) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("fun" in labels)
    }
    @Test
    fun query_offersDocumentIdentifiers() {
        val s = EditorSession("value1 = 0\nval x = va", EditorLanguage.Kotlin).also { it.setCaret(21) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("value1" in labels)
    }
    @Test
    fun accept_snippetInsertsAndPlacesCaretAtDollarZero() {
        val s = EditorSession("ma", EditorLanguage.Kotlin).also { it.setCaret(2) }
        val main = BuiltInCompletionProvider(EditorLanguage.Kotlin)
            .complete(controller.buildContext(s)).first { it.label == "main" }
        controller.accept(s, main)
        assertEquals("fun main() {\n    \n}", s.text)
        assertEquals(17, s.selection.caret)
    }
    @Test
    fun accept_isASingleUndoStep() {
        val s = EditorSession("pri", EditorLanguage.Kotlin).also { it.setCaret(3) }
        val item = controller.queryHeuristic(s).first { it.label == "private" }
        controller.accept(s, item)
        assertEquals("private", s.text)
        s.undo()
        assertEquals("pri", s.text)
    }
    @Test
    fun accept_importPartialSegment_replacesOnlyLastSegment() {
        val s = EditorSession("import androidx.act", EditorLanguage.Kotlin).also { it.setCaret(19) }
        val item = CompletionItem("activity", "activity", CompletionKind.Class)
        controller.accept(s, item)
        assertEquals("import androidx.activity", s.text)
        assertEquals(24, s.selection.caret)
    }
    @Test
    fun accept_importFqSuggestion_replacesWholeTypedPath() {
        val s = EditorSession("import androidx.act", EditorLanguage.Kotlin).also { it.setCaret(19) }
        val item = CompletionItem("androidx.activity", "androidx.activity", CompletionKind.Class)
        controller.accept(s, item)
        assertEquals("import androidx.activity", s.text)
        assertEquals(24, s.selection.caret)
    }
    @Test
    fun importAndroidxAct_suggestsActivity() {
        val s = EditorSession("import androidx.act", EditorLanguage.Kotlin).also { it.setCaret(19) }
        val labels = controller.queryHeuristic(s).map { it.label }
        assertTrue("activity" in labels)
    }
    @Test
    fun plainLanguage_hasNoKeywordOrSnippetNoise() {
        val s = EditorSession("fu", EditorLanguage.Plain).also { it.setCaret(2) }
        assertTrue(controller.queryHeuristic(s).none { it.kind == CompletionKind.Keyword || it.kind == CompletionKind.Snippet })
    }

    @Test
    fun query_namingNewFunction_returnsEmpty() {
        val text = "fun myNewFun"
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(text.length) }
        assertTrue(controller.queryHeuristic(s).isEmpty())
    }
    @Test
    fun query_namingNewVal_returnsEmpty() {
        val text = "val greeting"
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(text.length) }
        assertTrue(controller.queryHeuristic(s).isEmpty())
    }
    @Test
    fun query_namingNewClass_returnsEmpty() {
        val text = "class MyScreen"
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(text.length) }
        assertTrue(controller.queryHeuristic(s).isEmpty())
    }
    @Test
    fun autoPopup_notTriggeredByOpenParenOrBrace() {
        val text = "fun foo("
        val s = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(text.length) }
        assertFalse(controller.shouldAutoPopup(s, '('))
        val body = "fun foo() {"
        val s2 = EditorSession(body, EditorLanguage.Kotlin).also { it.setCaret(body.length) }
        assertFalse(controller.shouldAutoPopup(s2, '{'))
    }
}
