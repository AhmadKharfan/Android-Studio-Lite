package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBuildReadinessMonitorTest {

    private var answer: CloudBuildState = CloudBuildState.NotConnected
    private var checks = 0

    private fun TestScope.monitor(
        triggers: MutableSharedFlow<Unit> = MutableSharedFlow(),
        online: MutableStateFlow<Boolean> = MutableStateFlow(true),
        checker: suspend () -> CloudBuildState = { checks++; answer },
    ) = CloudBuildReadinessMonitor(checker, backgroundScope, triggers, online)

    @Test
    fun `state is unknown until the first check, then holds its answer`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor()
        assertNull(monitor.state.value)

        assertEquals(CloudBuildState.NotConnected, monitor.check())
        assertEquals(CloudBuildState.NotConnected, monitor.state.value)
    }

    @Test
    fun `concurrent checks share one github check`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val monitor = monitor(checker = { checks++; gate.await(); CloudBuildState.Offline })

        val first = async { monitor.check() }
        val second = async { monitor.check() }
        assertEquals(CloudBuildState.Checking, monitor.state.value)
        gate.complete(Unit)

        assertEquals(CloudBuildState.Offline, first.await())
        assertEquals(CloudBuildState.Offline, second.await())
        assertEquals(1, checks)
    }

    @Test
    fun `a trigger re-checks`() = runTest(UnconfinedTestDispatcher()) {
        val triggers = MutableSharedFlow<Unit>()
        val monitor = monitor(triggers = triggers)

        answer = CloudBuildState.Revoked
        triggers.emit(Unit)

        assertEquals(CloudBuildState.Revoked, monitor.state.value)
    }

    @Test
    fun `coming back online re-checks only after an offline answer`() = runTest(UnconfinedTestDispatcher()) {
        val online = MutableStateFlow(true)
        val monitor = monitor(online = online)
        answer = CloudBuildState.Offline
        monitor.check()
        online.value = false
        val before = checks

        answer = CloudBuildState.NotConnected
        online.value = true

        assertEquals(before + 1, checks)
        assertEquals(CloudBuildState.NotConnected, monitor.state.value)
    }

    @Test
    fun `a check that throws leaves an unknown state rather than checking forever`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = monitor(checker = { error("boom") })

        assertEquals(CloudBuildState.Unknown("boom"), monitor.check())
        assertEquals(CloudBuildState.Unknown("boom"), monitor.state.value)
    }

    @Test
    fun `a check outlives the caller that started it`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val monitor = monitor(checker = { gate.await(); CloudBuildState.NotConnected })

        val caller = async { monitor.check() }
        caller.cancel()
        gate.complete(Unit)

        assertEquals(CloudBuildState.NotConnected, monitor.state.value)
    }
}
