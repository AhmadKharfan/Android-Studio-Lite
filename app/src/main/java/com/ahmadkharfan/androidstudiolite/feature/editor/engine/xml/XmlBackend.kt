package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem

/** Completion candidates for an XML caret, plus the source range a chosen item replaces. */
data class XmlCompletionResult(val items: List<CompletionItem>, val replaceStart: Int, val replaceEnd: Int)

/**
 * Entry point for XML language features consumed by the editor.
 *
 * It ties together the parser, the completion analyzer and the Android catalog.
 */
object XmlBackend {

    private val contributors: List<XmlCompletionContributor> = listOf(AndroidXmlContributor)

    /** Completion candidates at [offset], filtered by the typed prefix and de-duplicated by label. */
    fun complete(text: String, offset: Int, filePath: String): XmlCompletionResult {
        val parsed = XmlParser(text).parse()
        val position = XmlCompletionAnalyzer.locate(text, offset, parsed, filePath)
        if (position.kind == XmlCompletionKind.UNKNOWN) {
            return XmlCompletionResult(emptyList(), offset, offset)
        }
        val items = contributors
            .flatMap { runCatching { it.contribute(position) }.getOrDefault(emptyList()) }
            .filter {
                XmlCompletionAnalyzer.prefixMatches(it.label, position.prefix) ||
                    XmlCompletionAnalyzer.prefixMatches(it.insertText, position.prefix)
            }
            .distinctBy { it.label }
            .sortedBy { it.label.lowercase() }
        return XmlCompletionResult(items, position.replaceStart, position.replaceEnd)
    }

    /** The (start, end) source range that accepting a completion at [offset] should replace. */
    fun replacementRangeAt(text: String, offset: Int, filePath: String): Pair<Int, Int> {
        val parsed = XmlParser(text).parse()
        val position = XmlCompletionAnalyzer.locate(text, offset, parsed, filePath)
        return position.replaceStart to position.replaceEnd
    }

    /** Whether typing [typedChar] at [offset] should auto-open the completion popup. */
    fun shouldAutoPopup(text: String, offset: Int, typedChar: Char, filePath: String): Boolean {
        if (typedChar == '<') return true
        val parsed = XmlParser(text).parse()
        val position = XmlCompletionAnalyzer.locate(text, offset, parsed, filePath)
        return when (typedChar) {
            '"', '\'' -> position.kind == XmlCompletionKind.ATTRIBUTE_VALUE
            ':' -> position.kind == XmlCompletionKind.ATTRIBUTE_NAME
            else -> (typedChar.isLetterOrDigit() || typedChar == '_' || typedChar == '-') &&
                position.kind in NAME_LIKE_KINDS
        }
    }

    private val NAME_LIKE_KINDS = setOf(
        XmlCompletionKind.TAG_NAME,
        XmlCompletionKind.ATTRIBUTE_NAME,
        XmlCompletionKind.ATTRIBUTE_VALUE,
    )
}
