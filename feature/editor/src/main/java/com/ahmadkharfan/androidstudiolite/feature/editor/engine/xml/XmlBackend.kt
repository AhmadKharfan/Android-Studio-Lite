package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.core.xml.XmlParser

data class XmlCompletionResult(val items: List<CompletionItem>, val replaceStart: Int, val replaceEnd: Int)

object XmlBackend {

    private val contributors: List<XmlCompletionContributor> = listOf(AndroidXmlContributor)

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

    fun replacementRangeAt(text: String, offset: Int, filePath: String): Pair<Int, Int> {
        val parsed = XmlParser(text).parse()
        val position = XmlCompletionAnalyzer.locate(text, offset, parsed, filePath)
        return position.replaceStart to position.replaceEnd
    }

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
