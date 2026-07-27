package com.ahmadkharfan.androidstudiolite.feature.editor.git

fun String.middleEllipsis(maxChars: Int = 48): String {
    if (length <= maxChars) return this
    val edge = (maxChars - 1) / 2
    return take(edge) + "…" + takeLast(maxChars - edge - 1)
}
