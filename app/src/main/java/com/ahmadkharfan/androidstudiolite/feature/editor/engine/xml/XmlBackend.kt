package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionItem
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.Diagnostic
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticCodes
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity

/** Completion candidates for an XML caret, plus the source range a chosen item replaces. */
data class XmlCompletionResult(val items: List<CompletionItem>, val replaceStart: Int, val replaceEnd: Int)

/**
 * Entry point for the XML language features consumed by the editor and the build tooling.
 *
 * It ties together the parser, the Android lint rules, the completion analyzer and the Android
 * catalog. Diagnostic codes, messages and severities match those the editor uses elsewhere; the
 * implementation is original to Android Studio Lite.
 */
object XmlBackend {

    private val contributors: List<XmlCompletionContributor> = listOf(AndroidXmlContributor)

    /** Structural + Android diagnostics for [text], sorted by start offset. */
    fun analyze(text: String, filePath: String): List<Diagnostic> {
        if (text.isEmpty()) return emptyList()

        val parsed = XmlParser(text).parse()
        val diagnostics = ArrayList<Diagnostic>()

        for (issue in parsed.issues) {
            diagnostics += Diagnostic(
                start = issue.start,
                end = issue.end,
                severity = DiagnosticSeverity.Error,
                message = issue.message,
                code = issue.code,
            )
        }

        for (ns in XmlLint.undeclaredNamespaces(parsed)) {
            diagnostics += Diagnostic(
                start = ns.start,
                end = ns.end,
                severity = DiagnosticSeverity.Error,
                message = "Missing xmlns:${ns.prefix} namespace declaration",
                code = DiagnosticCodes.ANDROID_MISSING_NAMESPACE,
            )
        }

        if (isLayoutResource(filePath)) {
            for (hit in XmlLint.hardcodedStrings(parsed)) {
                diagnostics += Diagnostic(
                    start = hit.start,
                    end = hit.end,
                    severity = DiagnosticSeverity.Warning,
                    message = "Hardcoded string should be a @string resource",
                    code = DiagnosticCodes.ANDROID_HARDCODED_TEXT,
                )
            }
            for (miss in XmlLint.missingDimensions(parsed)) {
                diagnostics += Diagnostic(
                    start = miss.start,
                    end = miss.end,
                    severity = DiagnosticSeverity.Warning,
                    message = "<${miss.tag}> is missing android:${miss.dimension}",
                    code = DiagnosticCodes.ANDROID_MISSING_SIZE,
                )
            }
        }

        return diagnostics.sortedBy { it.start }
    }

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

    private fun isLayoutResource(filePath: String): Boolean {
        val path = filePath.replace('\\', '/')
        val fileName = path.substringAfterLast('/')
        return path.contains("/res/layout") ||
            fileName.startsWith("activity_") ||
            fileName.startsWith("fragment_") ||
            path.contains("layout")
    }
}
