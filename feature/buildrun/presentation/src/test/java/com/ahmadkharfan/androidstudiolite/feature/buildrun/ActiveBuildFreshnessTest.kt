package com.ahmadkharfan.androidstudiolite.feature.buildrun

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveBuildFreshnessTest {

    @Test
    fun `build started within window is fresh`() {
        assertTrue(isActiveBuildFresh(nowMillis = 10_000L, startedAtEpochMs = 8_000L, maxAgeMs = 5_000L))
    }

    @Test
    fun `build older than window is stale`() {
        assertFalse(isActiveBuildFresh(nowMillis = 10_000L, startedAtEpochMs = 4_000L, maxAgeMs = 5_000L))
    }

    @Test
    fun `build at exact age boundary is fresh`() {
        assertTrue(isActiveBuildFresh(nowMillis = 10_000L, startedAtEpochMs = 5_000L, maxAgeMs = 5_000L))
    }

    @Test
    fun `build one millisecond beyond age boundary is stale`() {
        assertFalse(isActiveBuildFresh(nowMillis = 10_001L, startedAtEpochMs = 5_000L, maxAgeMs = 5_000L))
    }

    @Test
    fun `zero or negative elapsed time is fresh`() {
        assertTrue(isActiveBuildFresh(nowMillis = 10_000L, startedAtEpochMs = 10_000L, maxAgeMs = 5_000L))
        assertTrue(isActiveBuildFresh(nowMillis = 10_000L, startedAtEpochMs = 10_001L, maxAgeMs = 5_000L))
    }
}
