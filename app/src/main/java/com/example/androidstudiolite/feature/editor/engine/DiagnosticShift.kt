package com.example.androidstudiolite.feature.editor.engine
internal data class EditSpan(val start: Int, val removed: Int, val inserted: Int) {
    val delta: Int get() = inserted - removed
    val isNoOp: Boolean get() = removed == 0 && inserted == 0
}
internal fun diffEdit(old: String, new: String): EditSpan {
    if (old == new) return EditSpan(0, 0, 0)
    val maxPrefix = minOf(old.length, new.length)
    var p = 0
    while (p < maxPrefix && old[p] == new[p]) p++
    var s = 0
    val maxSuffix = minOf(old.length - p, new.length - p)
    while (s < maxSuffix && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    return EditSpan(p, old.length - p - s, new.length - p - s)
}
private fun mapStart(o: Int, edit: EditSpan): Int = when {
    o < edit.start -> o
    o == edit.start -> if (edit.removed == 0) o + edit.delta else o
    o < edit.start + edit.removed -> edit.start
    else -> o + edit.delta
}
private fun mapEnd(o: Int, edit: EditSpan): Int = when {
    o <= edit.start -> o
    o <= edit.start + edit.removed -> edit.start
    else -> o + edit.delta
}
fun shiftDiagnostics(diagnostics: List<Diagnostic>, old: String, new: String): List<Diagnostic> {
    if (diagnostics.isEmpty()) return diagnostics
    val edit = diffEdit(old, new)
    if (edit.isNoOp) return diagnostics
    val out = ArrayList<Diagnostic>(diagnostics.size)
    for (d in diagnostics) {
        val start = mapStart(d.start, edit).coerceIn(0, new.length)
        val end = mapEnd(d.end, edit).coerceIn(start, new.length)
        if (end <= start && d.end > d.start) continue
        out.add(d.copy(start = start, end = end))
    }
    return out
}
