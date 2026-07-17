package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitOperationType
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitOperationCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `mutations queue and active operation follows the exclusive owner`() = runTest {
        val repo = temp.newFolder("repo").also { File(it, ".git").mkdir() }
        val coordinator = GitOperationCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        var secondEntered = false

        val first = launch {
            coordinator.runExclusive(repo, GitOperationType.COMMIT) {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()
        assertEquals(GitOperationType.COMMIT, coordinator.activeOperation(repo).value?.type)

        val second = launch {
            coordinator.runExclusive(repo, GitOperationType.PUSH) { secondEntered = true }
        }
        runCurrent()
        assertFalse(secondEntered)
        assertEquals(GitOperationType.COMMIT, coordinator.activeOperation(repo).value?.type)

        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertTrue(secondEntered)
        assertNull(coordinator.activeOperation(repo).value)
    }

    @Test
    fun `cancellable operation can publish progress and be cancelled`() = runTest {
        val repo = temp.newFolder("cancel").also { File(it, ".git").mkdir() }
        val coordinator = GitOperationCoordinator()
        val entered = CompletableDeferred<Unit>()
        val operation = launch {
            coordinator.runExclusive(repo, GitOperationType.FETCH, cancellable = true) {
                coordinator.updateProgress(repo, 0.5f, "Receiving objects")
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()

        assertEquals(0.5f, coordinator.activeOperation(repo).value?.progress)
        assertEquals("Receiving objects", coordinator.activeOperation(repo).value?.message)
        assertTrue(coordinator.cancelActiveOperation(repo))
        operation.join()
        assertTrue(operation.isCancelled)
        assertNull(coordinator.activeOperation(repo).value)
    }
}
