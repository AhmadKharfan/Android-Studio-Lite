package com.ahmadkharfan.androidstudiolite.feature.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionRankingCharacterizationTest {

    private val controller = EditorCompletionController()

    @Test
    fun `built in name ranking retains kind and label order`() {
        val context = CompletionContext(
            language = EditorLanguage.Kotlin,
            text = "paint privateThing p",
            caret = 20,
            prefix = "p",
            prefixStart = 19,
            memberAccess = false,
            composeContext = true,
        )

        val items = BuiltInCompletionProvider(EditorLanguage.Kotlin).complete(context)

        assertEquals(
            listOf(
                "print|Function|print|function",
                "println|Snippet|null|null",
                "public|Keyword|null|null",
                "package|Keyword|null|null",
                "private|Keyword|null|null",
                "protected|Keyword|null|null",
                "paint|Variable|null|null",
                "Preview|Class|annotation|class",
                "privateThing|Variable|null|null",
            ),
            snapshot(items),
        )
    }

    @Test
    fun `controller name ranking retains merged kind and label order`() {
        assertEquals(
            listOf(
                "print|Function|print|function",
                "println|Snippet|null|null",
                "public|Keyword|null|null",
                "package|Keyword|null|null",
                "private|Keyword|null|null",
                "protected|Keyword|null|null",
                "paint|Variable|null|null",
                "privateThing|Variable|null|null",
            ),
            snapshot(completionAt("paint privateThing p|")),
        )
    }

    @Test
    fun `call argument ranking retains parameter order`() {
        assertEquals(
            listOf(
                "style =|Parameter|TextStyle|parameter",
                "fontSize =|Parameter|TextUnit|parameter",
                "maxLines =|Parameter|Int|parameter",
                "minLines =|Parameter|Int|parameter",
                "modifier =|Parameter|Modifier|parameter",
                "overflow =|Parameter|TextOverflow|parameter",
                "softWrap =|Parameter|Boolean|parameter",
                "fontStyle =|Parameter|FontStyle|parameter",
                "textAlign =|Parameter|TextAlign|parameter",
                "fontFamily =|Parameter|FontFamily|parameter",
                "fontWeight =|Parameter|FontWeight|parameter",
                "lineHeight =|Parameter|TextUnit|parameter",
            ),
            snapshot(completionAt("@Composable\nfun S() { Text(text = \"Hi\", color = |) }").take(12)),
        )
    }

    private fun completionAt(markedText: String): List<CompletionItem> {
        val caret = markedText.indexOf('|')
        val text = markedText.replace("|", "")
        val session = EditorSession(text, EditorLanguage.Kotlin).also { it.setCaret(caret) }
        return controller.queryHeuristic(session)
    }

    private fun snapshot(items: List<CompletionItem>): List<String> = items.map {
        "${it.label}|${it.kind}|${it.detail}|${it.typeText}"
    }
}
