package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeEvent
import com.ahmadkharfan.androidstudiolite.domain.model.RootInvalidationReason
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileChangeBusTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `root generations increase monotonically and events are not lost`() = runTest {
        val bus = FileChangeBus()
        val root = temp.newFolder("root").canonicalPath
        val events = async {
            bus.events.filterIsInstance<FileChangeEvent.RootInvalidated>().take(2).toList()
        }
        runCurrent()

        bus.emitRootInvalidated(root, RootInvalidationReason.GIT_OPERATION)
        bus.emitRootInvalidated(root, RootInvalidationReason.EXTERNAL)

        assertEquals(listOf(1L, 2L), events.await().map { it.generation })
        assertEquals(2L, bus.generations.value[root])
    }
}
