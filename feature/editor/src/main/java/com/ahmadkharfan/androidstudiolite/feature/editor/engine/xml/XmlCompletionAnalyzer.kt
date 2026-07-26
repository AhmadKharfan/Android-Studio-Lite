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
        val openTag = findEnclosingOpenTag(text, caret)
            ?: return textPosition(parsed, caret, filePath)
        when (text.getOrNull(openTag.angleIndex + 1)) {
            '/', '!', '?' -> return unknownPosition(caret, filePath)
        }
        val attributeScan = scanAttributes(text, openTag.nameEnd, caret)
        if (attributeScan.crossedTagEnd) return textPosition(parsed, caret, filePath)
        val parentTag = enclosingElementName(
            parsed,
            (openTag.angleIndex - 1).coerceAtLeast(0),
        )
        return classifyCaret(
            ClassificationContext(text, caret, openTag, parentTag, attributeScan, filePath),
        )
    }

    private fun findEnclosingOpenTag(text: CharSequence, caret: Int): OpenTag? {
        val angleIndex = previousIndexOf(text, '<', caret)
        if (angleIndex < 0) return null
        val nameEnd = nameEndAt(text, angleIndex + 1)
        val name = text.subSequence(angleIndex + 1, nameEnd).toString().ifEmpty { null }
        return OpenTag(angleIndex, nameEnd, name)
    }

    private fun scanAttributes(text: CharSequence, from: Int, caret: Int): AttributeScan {
        val scan = AttributeScan(index = from)
        while (scan.index < caret && !scan.crossedTagEnd) {
            val character = text[scan.index]
            if (scan.quote != null) {
                scanQuotedCharacter(scan, character)
            } else {
                scanUnquotedCharacter(text, scan, character)
            }
        }
        return scan
    }

    private fun scanQuotedCharacter(scan: AttributeScan, character: Char) {
        if (character == scan.quote) {
            scan.quote = null
            scan.currentAttribute = null
            scan.afterEquals = false
        }
        scan.index++
    }

    private fun scanUnquotedCharacter(
        text: CharSequence,
        scan: AttributeScan,
        character: Char,
    ) {
        when {
            character == '>' -> scan.crossedTagEnd = true
            character == '"' || character == '\'' -> {
                scan.quote = character
                scan.valueStart = scan.index + 1
                scan.index++
            }
            character == '=' -> {
                scan.afterEquals = true
                scan.index++
            }
            character.isWhitespace() || character == '/' -> scan.index++
            isNameStart(character) -> scanAttributeName(text, scan)
            else -> scan.index++
        }
    }

    private fun scanAttributeName(text: CharSequence, scan: AttributeScan) {
        val nameEnd = nameEndAt(text, scan.index)
        val attributeName = text.subSequence(scan.index, nameEnd).toString()
        scan.currentAttribute = attributeName
        scan.existingAttributes += attributeName
        scan.afterEquals = false
        scan.index = nameEnd
    }

    private fun classifyCaret(context: ClassificationContext): XmlCompletionPosition =
        when {
            context.attributeScan.quote != null -> quotedAttributeValuePosition(context)
            context.attributeScan.afterEquals &&
                context.attributeScan.currentAttribute != null -> emptyAttributeValuePosition(context)
            context.caret <= context.openTag.nameEnd -> tagNamePosition(context)
            else -> attributeNamePosition(context)
        }

    private fun quotedAttributeValuePosition(
        context: ClassificationContext,
    ): XmlCompletionPosition {
        val replacement = replacement(
            context.text,
            context.attributeScan.valueStart.coerceAtMost(context.caret),
            context.caret,
        )
        return XmlCompletionPosition(
            kind = XmlCompletionKind.ATTRIBUTE_VALUE,
            tag = context.openTag.name,
            parentTag = context.parentTag,
            attributeName = context.attributeScan.currentAttribute,
            existingAttributes = context.attributeScan.existingAttributes,
            prefix = replacement.prefix,
            replaceStart = replacement.start,
            replaceEnd = replacement.end,
            filePath = context.filePath,
        )
    }

    private fun emptyAttributeValuePosition(
        context: ClassificationContext,
    ): XmlCompletionPosition {
        val replacement = replacement(context.text, context.caret, context.caret)
        return XmlCompletionPosition(
            kind = XmlCompletionKind.ATTRIBUTE_VALUE,
            tag = context.openTag.name,
            parentTag = context.parentTag,
            attributeName = context.attributeScan.currentAttribute,
            existingAttributes = context.attributeScan.existingAttributes,
            prefix = replacement.prefix,
            replaceStart = replacement.start,
            replaceEnd = replacement.end,
            filePath = context.filePath,
        )
    }

    private fun tagNamePosition(context: ClassificationContext): XmlCompletionPosition {
        val replacement = replacement(
            context.text,
            context.openTag.angleIndex + 1,
            context.caret,
        )
        return XmlCompletionPosition(
            kind = XmlCompletionKind.TAG_NAME,
            tag = context.openTag.name,
            parentTag = context.parentTag,
            attributeName = null,
            existingAttributes = emptySet(),
            prefix = replacement.prefix,
            replaceStart = replacement.start,
            replaceEnd = replacement.end,
            filePath = context.filePath,
        )
    }

    private fun attributeNamePosition(context: ClassificationContext): XmlCompletionPosition {
        var tokenStart = context.caret
        while (
            tokenStart > context.openTag.angleIndex + 1 &&
            isNameChar(context.text[tokenStart - 1])
        ) {
            tokenStart--
        }
        val replacement = replacement(context.text, tokenStart, context.caret)
        context.attributeScan.existingAttributes.remove(replacement.prefix)
        return XmlCompletionPosition(
            kind = XmlCompletionKind.ATTRIBUTE_NAME,
            tag = context.openTag.name,
            parentTag = context.parentTag,
            attributeName = null,
            existingAttributes = context.attributeScan.existingAttributes,
            prefix = replacement.prefix,
            replaceStart = replacement.start,
            replaceEnd = replacement.end,
            filePath = context.filePath,
        )
    }

    private fun replacement(text: CharSequence, start: Int, end: Int) =
        Replacement(text.subSequence(start, end).toString(), start, end)

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

    private data class OpenTag(
        val angleIndex: Int,
        val nameEnd: Int,
        val name: String?,
    )

    private class AttributeScan(var index: Int) {
        var quote: Char? = null
        var valueStart: Int = -1
        var currentAttribute: String? = null
        var afterEquals: Boolean = false
        val existingAttributes = LinkedHashSet<String>()
        var crossedTagEnd: Boolean = false
    }

    private data class ClassificationContext(
        val text: CharSequence,
        val caret: Int,
        val openTag: OpenTag,
        val parentTag: String?,
        val attributeScan: AttributeScan,
        val filePath: String,
    )

    private data class Replacement(
        val prefix: String,
        val start: Int,
        val end: Int,
    )
}
