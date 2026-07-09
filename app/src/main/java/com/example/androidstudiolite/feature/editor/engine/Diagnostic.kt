package com.example.androidstudiolite.feature.editor.engine
enum class DiagnosticSeverity { Error, Warning, Info }
data class Diagnostic(
    val start: Int,
    val end: Int,
    val severity: DiagnosticSeverity,
    val message: String = "",
)
object DemoDiagnostics {
    fun analyze(text: String): List<Diagnostic> {
        val out = ArrayList<Diagnostic>()
        for (marker in arrayOf("TODO", "FIXME")) {
            var i = text.indexOf(marker)
            while (i >= 0) {
                out.add(Diagnostic(i, i + marker.length, DiagnosticSeverity.Warning, "$marker marker"))
                i = text.indexOf(marker, i + marker.length)
            }
        }
        var i = text.indexOf("fooBar")
        while (i >= 0) {
            val before = if (i > 0) text[i - 1] else ' '
            val after = if (i + 6 < text.length) text[i + 6] else ' '
            val isWord = (before.isLetterOrDigit() || before == '_') || (after.isLetterOrDigit() || after == '_')
            if (!isWord) out.add(Diagnostic(i, i + 6, DiagnosticSeverity.Error, "unresolved reference: fooBar"))
            i = text.indexOf("fooBar", i + 6)
        }
        return out
    }
}
