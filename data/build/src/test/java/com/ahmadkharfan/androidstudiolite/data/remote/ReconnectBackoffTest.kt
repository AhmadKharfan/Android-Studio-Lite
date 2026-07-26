package com.ahmadkharfan.androidstudiolite.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun `backoff doubles until capped and stays capped`() {
        val expectedMillis = listOf(
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            30_000L,
            30_000L,
            30_000L,
        )

        assertEquals(expectedMillis, (0..7).map(::reconnectBackoffMillis))
        assertEquals(30_000L, reconnectBackoffMillis(50))
    }
}
