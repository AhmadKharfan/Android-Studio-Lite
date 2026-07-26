package com.ahmadkharfan.androidstudiolite.feature.terminal

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionManagerTest {

    private class FakeTerminalRepository : TerminalRepository {
        override val events: SharedFlow<TerminalEvent> = MutableSharedFlow()
        override suspend fun start(workingDirectory: String?, rows: Int, cols: Int) {}
        override suspend fun send(command: String) {}
        override suspend fun stop() {}
    }

    private fun manager() = TerminalSessionManager(repositoryFactory = { _ -> FakeTerminalRepository() })

    @Test
    fun ensureSession_creates_exactly_one_tab() {
        val mgr = manager()
        mgr.ensureSession(24, 80)
        mgr.ensureSession(24, 80)
        assertEquals(1, mgr.sessions.value.size)
        assertEquals(mgr.sessions.value.first().id, mgr.activeId.value)
    }

    @Test
    fun newSession_adds_a_tab_and_activates_it_without_closing_others() {
        val mgr = manager()
        mgr.ensureSession(24, 80)
        val first = mgr.activeId.value
        val second = mgr.newSession(24, 80)
        assertEquals(2, mgr.sessions.value.size)
        assertEquals(second, mgr.activeId.value)
        assertNotEquals(first, second)
    }

    @Test
    fun select_switches_active_tab() {
        val mgr = manager()
        mgr.ensureSession(24, 80)
        val first = mgr.activeId.value!!
        mgr.newSession(24, 80)
        mgr.select(first)
        assertEquals(first, mgr.activeId.value)
    }

    @Test
    fun closing_active_tab_activates_a_neighbour() {
        val mgr = manager()
        mgr.ensureSession(24, 80)
        val first = mgr.activeId.value!!
        val second = mgr.newSession(24, 80)
        mgr.select(first)
        mgr.close(first)
        assertEquals(1, mgr.sessions.value.size)
        assertEquals(second, mgr.activeId.value)
        assertTrue(mgr.sessions.value.none { it.id == first })
    }
}
