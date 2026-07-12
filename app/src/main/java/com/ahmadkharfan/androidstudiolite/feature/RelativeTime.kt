package com.ahmadkharfan.androidstudiolite.feature

/** Formats a past timestamp as a short human phrase, e.g. "2 hours ago", "yesterday". */
fun formatRelativeTime(millis: Long): String {
    val diffMs = System.currentTimeMillis() - millis
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}
