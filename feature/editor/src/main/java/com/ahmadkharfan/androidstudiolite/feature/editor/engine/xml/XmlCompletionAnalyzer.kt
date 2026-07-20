package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.core.xml.ParsedXml
import com.ahmadkharfan.androidstudiolite.core.xml.XmlNode
import com.ahmadkharfan.androidstudiolite.core.xml.XmlNodeType

enum class XmlCompletionKind { TAG_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, TEXT, UNKNOWN }

data class XmlCompletionPosition(
    val kind: XmlCompletionKind,
    val tag: String?,
    val parentTag: String?,
    val attributeName: String?,
    val existingAttributes: Set<String>,
    val prefix: String,
    val replaceStart: Int,
    val replaceEnd: Int,
    val filePath: String,
)

fun interface XmlCompletionContributor {
    fun contribute(position: XmlCompletionPosition): List<CompletionItem>
}

object XmlCompletionAnalyzer {

    fun locate(text: CharSequence, offset: Int, parsed: ParsedXml, filePath: String): XmlCompletionPosition {
        val caret = offset.coerceIn(0, text.length)

        val openAngle = previousIndexOf(text, '<', caret)
        if (openAngle < 0) return textPosition(parsed, caret, filePath)


        when (text.getOrNull(openAngle + 1)) {
            '/', '!', '?' -> return unknownPosition(caret, filePath)
        }

        val tagNameEnd = nameEndAt(text, openAngle + 1)
        val tagName = text.subSequence(openAngle + 1, tagNameEnd).toString().ifEmpty { null }


        var i = tagNameEnd
        var quote: Char? = null
        var valueStart = -1
        var currentAttr: String? = null
        var afterEquals = false
        val present = LinkedHashSet<String>()

        while (i < caret) {
            val c = text[i]
            if (quote != null) {
                if (c == quote) {
                    quote = null
                    currentAttr = null
                    afterEquals = false
                }
                i++
                continue
            }
            when {
                c == '>' -> return textPosition(parsed, caret, filePath)
                c == '"' || c == '\'' -> {
                    quote = c
                    valueStart = i + 1
                    i++
                }
                c == '=' -> {
                    afterEquals = true
                    i++
                }
                c.isWhitespace() || c == '/' -> i++
                isNameStart(c) -> {
                    val nameEnd = nameEndAt(text, i)
                    currentAttr = text.subSequence(i, nameEnd).toString()
                    present += currentAttr!!
                    afterEquals = false
                    i = nameEnd
                }
                else -> i++
            }
        }

        val parentTag = enclosingElementName(parsed, (openAngle - 1).coerceAtLeast(0))


        if (quote != null) {
            val from = valueStart.coerceAtMost(caret)
            return XmlCompletionPosition(
                kind = XmlCompletionKind.ATTRIBUTE_VALUE,
                tag = tagName,
                parentTag = parentTag,
                attributeName = currentAttr,
                existingAttributes = present,
                prefix = text.subSequence(from, caret).toString(),
                replaceStart = from,
                replaceEnd = caret,
                filePath = filePath,
            )
        }


        if (afterEquals && currentAttr != null) {
            return XmlCompletionPosition(
                kind = XmlCompletionKind.ATTRIBUTE_VALUE,
                tag = tagName,
                parentTag = parentTag,
                attributeName = currentAttr,
                existingAttributes = present,
                prefix = "",
                replaceStart = caret,
                replaceEnd = caret,
                filePath = filePath,
            )
        }


        if (caret <= tagNameEnd) {
            return XmlCompletionPosition(
                kind = XmlCompletionKind.TAG_NAME,
                tag = tagName,
                parentTag = parentTag,
                attributeName = null,
                existingAttributes = emptySet(),
                prefix = text.subSequence(openAngle + 1, caret).toString(),
                replaceStart = openAngle + 1,
                replaceEnd = caret,
                filePath = filePath,
            )
        }


        var tokenStart = caret
        while (tokenStart > openAngle + 1 && isNameChar(text[tokenStart - 1])) tokenStart--
        present.remove(text.subSequence(tokenStart, caret).toString())
        return XmlCompletionPosition(
            kind = XmlCompletionKind.ATTRIBUTE_NAME,
            tag = tagName,
            parentTag = parentTag,
            attributeName = null,
            existingAttributes = present,
            prefix = text.subSequence(tokenStart, caret).toString(),
            replaceStart = tokenStart,
            replaceEnd = caret,
            filePath = filePath,
        )
    }

    fun prefixMatches(candidate: String, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        if (candidate.startsWith(prefix, ignoreCase = true)) return true
        for (separator in charArrayOf(':', '/', '.')) {
            val segment = candidate.substringAfterLast(separator, "")
            if (segment.isNotEmpty() && segment.startsWith(prefix, ignoreCase = true)) return true
        }
        return false
    }

    private fun textPosition(parsed: ParsedXml, caret: Int, filePath: String) = XmlCompletionPosition(
        kind = XmlCompletionKind.TEXT,
        tag = null,
        parentTag = enclosingElementName(parsed, caret),
        attributeName = null,
        existingAttributes = emptySet(),
        prefix = "",
        replaceStart = caret,
        replaceEnd = caret,
        filePath = filePath,
    )

    private fun unknownPosition(caret: Int, filePath: String) = XmlCompletionPosition(
        kind = XmlCompletionKind.UNKNOWN,
        tag = null,
        parentTag = null,
        attributeName = null,
        existingAttributes = emptySet(),
        prefix = "",
        replaceStart = caret,
        replaceEnd = caret,
        filePath = filePath,
    )

    private fun enclosingElementName(parsed: ParsedXml, offset: Int): String? {
        var node: XmlNode? = parsed.nodeCovering(offset)
        while (node != null && node.type != XmlNodeType.ELEMENT) node = node.parent
        return node?.name?.ifEmpty { null }
    }

    private fun previousIndexOf(text: CharSequence, ch: Char, before: Int): Int {
        for (i in before - 1 downTo 0) if (text[i] == ch) return i
        return -1
    }

    private fun nameEndAt(text: CharSequence, from: Int): Int {
        var i = from
        if (i < text.length && isNameStart(text[i])) {
            i++
            while (i < text.length && isNameChar(text[i])) i++
        }
        return i
    }

    private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
    private fun isNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == ':' || c == '.' || c == '-'
}
