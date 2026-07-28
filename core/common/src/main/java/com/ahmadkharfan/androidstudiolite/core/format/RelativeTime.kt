package com.ahmadkharfan.androidstudiolite.core.format

import android.content.Context
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val value: Int) : RelativeTime
}

fun relativeTimeOf(thenMillis: Long, nowMillis: Long): RelativeTime {
    if (thenMillis > nowMillis) return RelativeTime.JustNow
    val minutes = (nowMillis - thenMillis) / MILLIS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        minutes < 1 -> RelativeTime.JustNow
        minutes < MINUTES_PER_HOUR -> RelativeTime.Minutes(minutes.toInt())
        hours < HOURS_PER_DAY -> RelativeTime.Hours(hours.toInt())
        days == 1L -> RelativeTime.Yesterday
        else -> RelativeTime.Days(days.toInt())
    }
}

fun formatRelativeTime(
    context: Context,
    millis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String = when (val relativeTime = relativeTimeOf(millis, nowMillis)) {
    RelativeTime.JustNow -> context.getString(CommonR.string.time_just_now)
    is RelativeTime.Minutes -> context.resources.getQuantityString(
        CommonR.plurals.time_minutes_ago,
        relativeTime.value,
        relativeTime.value,
    )
    is RelativeTime.Hours -> context.resources.getQuantityString(
        CommonR.plurals.time_hours_ago,
        relativeTime.value,
        relativeTime.value,
    )
    RelativeTime.Yesterday -> context.getString(CommonR.string.time_yesterday)
    is RelativeTime.Days -> context.resources.getQuantityString(
        CommonR.plurals.time_days_ago,
        relativeTime.value,
        relativeTime.value,
    )
}
