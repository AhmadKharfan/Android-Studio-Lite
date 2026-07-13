package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem

/** What the caret is positioned to complete. */
enum class XmlCompletionKind { TAG_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, TEXT, UNKNOWN }

/**
 * A fully resolved completion site: what we are completing, the enclosing tag/parent, the attribute
 * being valued (if any), attributes already present on the current tag, the typed [prefix], and the
 * source range that a chosen item should replace.
 */
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

/** Contributes completion candidates for a resolved [XmlCompletionPosition]. */
fun interface XmlCompletionContributor {
    fun contribute(position: XmlCompletionPosition): List<CompletionItem>
}

/**
 * Determines what should be completed at a caret offset by scanning the raw text backwards to the
 * nearest `<`, then forward through the start tag while tracking attribute/quote state.
 *
 * Original implementation for Android Studio Lite.
 */
object XmlCompletionAnalyzer {

    fun locate(text: CharSequence, offset: Int, parsed: ParsedXml, filePath: String): XmlCompletionPosition {
        val caret = offset.coerceIn(0, text.length)

        val openAngle = previousIndexOf(text, '<', caret)
        if (openAngle < 0) return textPosition(parsed, caret, filePath)

        // A closing tag, comment, PI or declaration is not a completion site.
        when (text.getOrNull(openAngle + 1)) {
            '/', '!', '?' -> return unknownPosition(caret, filePath)
        }

        val tagNameEnd = nameEndAt(text, openAngle + 1)
        val tagName = text.subSequence(openAngle + 1, tagNameEnd).toString().ifEmpty { null }

        // Walk the start tag, tracking the current attribute, whether we've passed '=', quote state,
        // and which attribute names already appear.
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

        // Inside an open quoted value.
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

        // Just past `attr=` but before any quote.
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

        // Still within the tag name itself.
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

        // Typing an attribute name. The token under the caret is being edited, so don't treat it as
        // "already present" when suppressing duplicates.
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

    /**
     * Prefix match used to filter candidates. Besides a plain leading match, we also match on the
     * segment after `:`, `/` or `.` so that typing `layout_w` reaches `android:layout_width`.
     */
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
