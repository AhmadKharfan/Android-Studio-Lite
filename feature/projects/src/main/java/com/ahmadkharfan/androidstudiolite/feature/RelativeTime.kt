package com.ahmadkharfan.androidstudiolite.feature

import android.content.Context
import com.ahmadkharfan.androidstudiolite.feature.projects.R

/** Formats a past timestamp as a short human phrase, localized via [context]. */
fun formatRelativeTime(context: Context, millis: Long): String {
    val diffMs = System.currentTimeMillis() - millis
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.resources.getQuantityString(R.plurals.time_minutes_ago, minutes.toInt(), minutes.toInt())
        hours < 24 -> context.resources.getQuantityString(R.plurals.time_hours_ago, hours.toInt(), hours.toInt())
        days == 1L -> context.getString(R.string.time_yesterday)
        else -> context.resources.getQuantityString(R.plurals.time_days_ago, days.toInt(), days.toInt())
    }
}
