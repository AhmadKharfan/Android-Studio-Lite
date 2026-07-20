package com.ahmadkharfan.androidstudiolite.data.local

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitRefreshPipelineTest {
    @Test
    fun `rapid debounced triggers coalesce into one scan`() = runTest {
        var scans = 0
        val pipeline = GitRefreshPipeline(backgroundScope) { _, _ -> scans++ }

        pipeline.requestDebounced(includeIgnored = false)
        pipeline.requestDebounced(includeIgnored = false)
        runCurrent()
        assertEquals(0, scans)

        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, scans)
    }

    @Test
    fun `immediate trigger bypasses pending debounce`() = runTest {
        var scans = 0
        val pipeline = GitRefreshPipeline(backgroundScope) { _, _ -> scans++ }
        pipeline.requestDebounced(includeIgnored = false)

        val immediate = async { pipeline.requestImmediate(includeIgnored = false) }
        runCurrent()

        assertEquals(1, scans)
        immediate.await()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, scans)
    }
}
