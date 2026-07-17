package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteHandler
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWorkspaceWriteGateTest {
    @Test
    fun `registered editor handler prepares the workspace until it is closed`() = runTest {
        val gate = DefaultWorkspaceWriteGate()
        val root = File("/projects/sample")
        var prepared = false
        val registration = gate.register(root, WorkspaceWriteHandler { prepared = true })

        gate.prepareForWorktreeMutation(File("/projects/../projects/sample"))
        assertTrue(prepared)

        registration.close()
        prepared = false
        gate.prepareForWorktreeMutation(root)
        assertFalse(prepared)
    }
}
