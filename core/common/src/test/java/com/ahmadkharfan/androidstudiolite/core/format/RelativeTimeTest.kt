package com.ahmadkharfan.androidstudiolite.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR
private const val NOW_MILLIS = 10 * MILLIS_PER_DAY

class RelativeTimeTest {

    @Test
    fun `keeps exact zero in just now bucket`() {
        assertEquals(RelativeTime.JustNow, relativeTimeOf(NOW_MILLIS, NOW_MILLIS))
    }

    @Test
    fun `switches from just now to minutes at one minute`() {
        assertEquals(RelativeTime.JustNow, relativeTimeOf(NOW_MILLIS - MILLIS_PER_MINUTE + 1, NOW_MILLIS))
        assertEquals(RelativeTime.Minutes(1), relativeTimeOf(NOW_MILLIS - MILLIS_PER_MINUTE, NOW_MILLIS))
    }

    @Test
    fun `switches from minutes to hours at sixty minutes`() {
        assertEquals(RelativeTime.Minutes(59), relativeTimeOf(NOW_MILLIS - 59 * MILLIS_PER_MINUTE, NOW_MILLIS))
        assertEquals(RelativeTime.Hours(1), relativeTimeOf(NOW_MILLIS - 60 * MILLIS_PER_MINUTE, NOW_MILLIS))
    }

    @Test
    fun `switches from hours to yesterday at twenty four hours`() {
        assertEquals(RelativeTime.Hours(23), relativeTimeOf(NOW_MILLIS - 23 * MILLIS_PER_HOUR, NOW_MILLIS))
        assertEquals(RelativeTime.Yesterday, relativeTimeOf(NOW_MILLIS - 24 * MILLIS_PER_HOUR, NOW_MILLIS))
    }

    @Test
    fun `switches from yesterday to days at two days`() {
        assertEquals(RelativeTime.Yesterday, relativeTimeOf(NOW_MILLIS - 2 * MILLIS_PER_DAY + 1, NOW_MILLIS))
        assertEquals(RelativeTime.Days(2), relativeTimeOf(NOW_MILLIS - 2 * MILLIS_PER_DAY, NOW_MILLIS))
    }

    @Test
    fun `reports values in the days bucket`() {
        assertEquals(RelativeTime.Days(3), relativeTimeOf(NOW_MILLIS - 3 * MILLIS_PER_DAY, NOW_MILLIS))
    }

    @Test
    fun `clamps future timestamps to just now`() {
        assertEquals(RelativeTime.JustNow, relativeTimeOf(NOW_MILLIS + MILLIS_PER_DAY, NOW_MILLIS))
    }
}
