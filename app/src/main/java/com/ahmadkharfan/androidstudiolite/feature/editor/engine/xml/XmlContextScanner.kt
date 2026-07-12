package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

enum class XmlCompletionKind { TAG_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, TEXT, UNKNOWN }

data class XmlCompletionPosition(
    val kind: XmlCompletionKind,
    val tag: String?,
    val parentTag: String?,
    val attributeName: String?,
    val existingAttributes: Set<String>,
    val prefix: String,
    val replacementRange: XmlRange,
    val filePath: String,
    val declaredNamespaces: Set<String> = emptySet(),
    val namespaceInsertOffset: Int = -1,
)

object XmlContextScanner {

    fun scan(text: CharSequence, offset: Int, parsed: XmlParsedFile, filePath: String): XmlCompletionPosition {
        val caret = offset.coerceIn(0, text.length)

        val lt = lastIndexOfBefore(text, '<', caret)
        if (lt < 0) return content(parsed, caret, filePath)
        val afterLt = if (lt + 1 < text.length) text[lt + 1] else ' '
        if (afterLt == '/' || afterLt == '!' || afterLt == '?') return unknown(filePath, caret)

        var i = lt + 1
        val nameEnd = readNameEnd(text, i)
        i = nameEnd
        var inQuote: Char? = null
        var valueStart = -1
        var curAttr: String? = null
        var sawEquals = false
        val existing = LinkedHashSet<String>()

        while (i < caret) {
            val c = text[i]
            if (inQuote != null) {
                if (c == inQuote) { inQuote = null; curAttr = null; sawEquals = false }
                i++
                continue
            }
            when {
                c == '>' -> return content(parsed, caret, filePath)
                c == '"' || c == '\'' -> { inQuote = c; valueStart = i + 1; i++ }
                c == '=' -> { sawEquals = true; i++ }
                c.isWhitespace() || c == '/' -> i++
                isNameStart(c) -> {
                    val s = i
                    val e = readNameEnd(text, i)
                    curAttr = text.subSequence(s, e).toString()
                    existing.add(curAttr!!)
                    sawEquals = false
                    i = e
                }
                else -> i++
            }
        }

        val tagName = text.subSequence(lt + 1, nameEnd).toString()
        val parentTag = enclosingTagName(parsed, (lt - 1).coerceAtLeast(0))

        if (inQuote != null) {
            return XmlCompletionPosition(
                kind = XmlCompletionKind.ATTRIBUTE_VALUE,
                tag = tagName.ifEmpty { null },
                parentTag = parentTag,
                attributeName = curAttr,
                existingAttributes = existing,
                prefix = text.subSequence(valueStart.coerceAtMost(caret), caret).toString(),
                replacementRange = XmlRange(valueStart.coerceAtMost(caret), caret),
                filePath = filePath,
            )
        }
        if (sawEquals && curAttr != null) {
            return XmlCompletionPosition(
                kind = XmlCompletionKind.ATTRIBUTE_VALUE,
                tag = tagName.ifEmpty { null },
                parentTag = parentTag,
                attributeName = curAttr,
                existingAttributes = existing,
                prefix = "",
                replacementRange = XmlRange(caret, caret),
                filePath = filePath,
            )
        }
        if (caret <= nameEnd) {
            return XmlCompletionPosition(
                kind = XmlCompletionKind.TAG_NAME,
                tag = tagName.ifEmpty { null },
                parentTag = parentTag,
                attributeName = null,
                existingAttributes = emptySet(),
                prefix = text.subSequence(lt + 1, caret).toString(),
                replacementRange = XmlRange(lt + 1, caret),
                filePath = filePath,
            )
        }
        var tokenStart = caret
        while (tokenStart > lt + 1 && isNameChar(text[tokenStart - 1])) tokenStart--
        existing.remove(text.subSequence(tokenStart, caret).toString())
        val root = rootTag(parsed)
        return XmlCompletionPosition(
            kind = XmlCompletionKind.ATTRIBUTE_NAME,
            tag = tagName.ifEmpty { null },
            parentTag = parentTag,
            attributeName = null,
            existingAttributes = existing,
            prefix = text.subSequence(tokenStart, caret).toString(),
            replacementRange = XmlRange(tokenStart, caret),
            filePath = filePath,
            declaredNamespaces = declaredNamespaces(root),
            namespaceInsertOffset = root?.let { it.startOffset + 1 + (it.name?.length ?: 0) } ?: -1,
        )
    }

    private fun declaredNamespaces(root: XmlNode?): Set<String> =
        root?.attributes?.mapNotNull { it.name }
            ?.filter { it.startsWith("xmlns:") }
            ?.mapTo(LinkedHashSet()) { it.removePrefix("xmlns:") } ?: emptySet()

    private fun rootTag(parsed: XmlParsedFile): XmlNode? {
        var found: XmlNode? = null
        fun walk(n: XmlNode) {
            if (found != null) return
            if (n.kind == XmlNodeKinds.TAG) { found = n; return }
            n.children.forEach(::walk)
        }
        walk(parsed.root)
        return found
    }

    private fun content(parsed: XmlParsedFile, caret: Int, filePath: String) = XmlCompletionPosition(
        kind = XmlCompletionKind.TEXT,
        tag = null,
        parentTag = enclosingTagName(parsed, caret),
        attributeName = null,
        existingAttributes = emptySet(),
        prefix = "",
        replacementRange = XmlRange(caret, caret),
        filePath = filePath,
    )

    private fun unknown(filePath: String, caret: Int) = XmlCompletionPosition(
        XmlCompletionKind.UNKNOWN, null, null, null, emptySet(), "", XmlRange(caret, caret), filePath,
    )

    private fun enclosingTagName(parsed: XmlParsedFile, offset: Int): String? {
        var node: XmlNode? = parsed.nodeAt(offset)
        while (node != null && node.kind != XmlNodeKinds.TAG) node = node.parent
        return node?.name?.ifEmpty { null }
    }

    private fun lastIndexOfBefore(text: CharSequence, ch: Char, before: Int): Int {
        var i = before - 1
        while (i >= 0) { if (text[i] == ch) return i; i-- }
        return -1
    }

    private fun readNameEnd(text: CharSequence, from: Int): Int {
        var i = from
        if (i < text.length && isNameStart(text[i])) {
            i++
            while (i < text.length && isNameChar(text[i])) i++
        }
        return i
    }

    private fun isNameStart(c: Char): Boolean = c.isLetter() || c == '_' || c == ':'
    private fun isNameChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == ':' || c == '.' || c == '-'

    fun nameMatches(candidate: String, prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        if (candidate.startsWith(prefix, ignoreCase = true)) return true
        val afterColon = candidate.substringAfter(':', "")
        if (afterColon.isNotEmpty() && afterColon.startsWith(prefix, ignoreCase = true)) return true
        val afterSlash = candidate.substringAfterLast('/', "")
        if (afterSlash.isNotEmpty() && afterSlash.startsWith(prefix, ignoreCase = true)) return true
        val afterDot = candidate.substringAfterLast('.', "")
        return afterDot.isNotEmpty() && afterDot.startsWith(prefix, ignoreCase = true)
    }
}

fun interface XmlCompletionContributor {
    fun contribute(position: XmlCompletionPosition): List<com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem>
}
