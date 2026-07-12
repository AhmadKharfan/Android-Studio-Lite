package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity

data class XmlCompletionResult(val items: List<CompletionItem>, val replaceStart: Int, val replaceEnd: Int)

object XmlBackend {

    private val contributors = listOf(AndroidXmlContributor)

    fun analyze(text: String, filePath: String): List<Diagnostic> {
        if (text.isEmpty()) return emptyList()
        val parsed = XmlTreeParser(text).parse()
        val path = filePath.replace('\\', '/')
        val isLayout = path.contains("/res/layout") || path.substringAfterLast('/').startsWith("activity_") ||
            path.substringAfterLast('/').startsWith("fragment_") || path.contains("layout")
        val out = ArrayList<Diagnostic>()

        for (d in parsed.diagnostics) {
            out += Diagnostic(d.start, d.end, DiagnosticSeverity.Error, d.message, code = d.code)
        }
        for (hit in XmlLintRules.missingNamespaces(parsed)) {
            out += Diagnostic(
                hit.range.start, hit.range.end, DiagnosticSeverity.Error,
                "Missing xmlns:${hit.prefix} namespace declaration", code = "android.missingNamespace",
            )
        }
        if (isLayout) {
            for (h in XmlLintRules.hardcodedText(parsed)) {
                out += Diagnostic(
                    h.range.start, h.range.end, DiagnosticSeverity.Warning,
                    "Hardcoded string should be a @string resource", muted = false, code = "android.hardcodedText",
                )
            }
            for (m in XmlLintRules.missingSize(parsed, ::isViewLike)) {
                out += Diagnostic(
                    m.range.start, m.range.end, DiagnosticSeverity.Warning,
                    "<${m.tag}> is missing android:${m.dim}", code = "android.missingSize",
                )
            }
        }
        return out.sortedBy { it.start }
    }

    fun complete(text: String, offset: Int, filePath: String): XmlCompletionResult {
        val parsed = XmlTreeParser(text).parse()
        val pos = XmlContextScanner.scan(text, offset, parsed, filePath)
        if (pos.kind == XmlCompletionKind.UNKNOWN) {
            return XmlCompletionResult(emptyList(), offset, offset)
        }
        val candidates = contributors.flatMap { runCatching { it.contribute(pos) }.getOrDefault(emptyList()) }
        val items = candidates
            .filter { XmlContextScanner.nameMatches(it.label, pos.prefix) || XmlContextScanner.nameMatches(it.insertText, pos.prefix) }
            .distinctBy { it.label }
            .sortedBy { it.label.lowercase() }
        return XmlCompletionResult(items, pos.replacementRange.start, pos.replacementRange.end)
    }

    fun replacementRangeAt(text: String, offset: Int, filePath: String): Pair<Int, Int> {
        val parsed = XmlTreeParser(text).parse()
        val pos = XmlContextScanner.scan(text, offset, parsed, filePath)
        return pos.replacementRange.start to pos.replacementRange.end
    }

    fun shouldAutoPopup(text: String, offset: Int, typedChar: Char, filePath: String): Boolean {
        if (typedChar == '<') return true
        val parsed = XmlTreeParser(text).parse()
        val pos = XmlContextScanner.scan(text, offset, parsed, filePath)
        return when (typedChar) {
            '"', '\'' -> pos.kind == XmlCompletionKind.ATTRIBUTE_VALUE
            ':' -> pos.kind == XmlCompletionKind.ATTRIBUTE_NAME
            else -> (typedChar.isLetterOrDigit() || typedChar == '_' || typedChar == '-') &&
                pos.kind in setOf(XmlCompletionKind.TAG_NAME, XmlCompletionKind.ATTRIBUTE_NAME, XmlCompletionKind.ATTRIBUTE_VALUE)
        }
    }

    private fun isViewLike(tag: String): Boolean {
        val simple = tag.substringAfterLast('.')
        return simple.firstOrNull()?.isUpperCase() == true
    }
}
