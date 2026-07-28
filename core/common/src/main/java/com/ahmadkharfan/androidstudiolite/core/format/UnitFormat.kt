package com.ahmadkharfan.androidstudiolite.core.format

import java.util.Locale

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = BYTES_PER_KB * BYTES_PER_KB
private const val MILLIS_PER_SECOND = 1000.0

fun formatBytes(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> String.format(Locale.ROOT, "%.1f MB", bytes / BYTES_PER_MB.toDouble())
    bytes >= BYTES_PER_KB -> String.format(Locale.ROOT, "%.1f KB", bytes / BYTES_PER_KB.toDouble())
    else -> "$bytes B"
}

fun formatSeconds(millis: Long): String =
    String.format(Locale.ROOT, "%.1fs", millis / MILLIS_PER_SECOND)
