package com.ahmadkharfan.androidstudiolite.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelCancellationTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `tryToExecute rethrows cancellation without rendering an error`() = runTest(dispatcher) {
        val viewModel = TestViewModel(dispatcher)
        var renderedError = false

        val job = viewModel.execute(onError = { renderedError = true }) { awaitCancellation() }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(renderedError)
    }

    @Test
    fun `tryToCollect rethrows cancellation without rendering an error`() = runTest(dispatcher) {
        val viewModel = TestViewModel(dispatcher)
        var renderedError = false

        val job = viewModel.collect(onError = { renderedError = true }) { flow { awaitCancellation() } }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(renderedError)
    }

    private class TestViewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher) :
        BaseViewModel<Unit, Nothing>(Unit, dispatcher) {
        fun execute(onError: (Throwable) -> Unit, block: suspend () -> Unit): Job =
            tryToExecute(block = block, onError = onError)

        fun collect(onError: (Throwable) -> Unit, block: suspend () -> Flow<Unit>): Job =
            tryToCollect(block = block, onCollect = {}, onError = onError)
    }
}
