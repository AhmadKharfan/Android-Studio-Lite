package com.ahmadkharfan.androidstudiolite.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedWaitTest {

    @Test
    fun `wait is capped by remaining time and never negative`() {
        val waitCases = listOf(
            Triple(5_000L, 30_000L, 5_000L),
            Triple(30_000L, 2_000L, 2_000L),
            Triple(5_000L, 5_000L, 5_000L),
            Triple(5_000L, 0L, 0L),
            Triple(5_000L, -1L, 0L),
        )

        waitCases.forEach { (desiredMillis, remainingMillis, expectedMillis) ->
            assertEquals(expectedMillis, boundedWaitMillis(desiredMillis, remainingMillis))
        }
    }
}
