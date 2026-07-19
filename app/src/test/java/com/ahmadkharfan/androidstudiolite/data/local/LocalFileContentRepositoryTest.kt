package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.FileChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.FileTooLargeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileContentRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val bus = FileChangeBus()
    private val repo = LocalFileContentRepository(changeBus = bus)

    @Test
    fun `writes and reads back utf8 text including non-ascii`() = runBlocking {
        val path = File(tmp.root, "notes.txt").absolutePath
        val text = "héllo — 世界 🚀\nsecond line\n"

        repo.writeText(path, text)

        assertEquals(text, repo.readText(path))
        // Bytes on disk match exactly (the manual-verification requirement, automated).
        assertEquals(text, File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `writeText creates missing parent directories`() = runBlocking {
        val path = File(tmp.root, "a/b/c/deep.kt").absolutePath

        repo.writeText(path, "package a\n")

        assertTrue(File(path).exists())
        assertEquals("package a\n", File(path).readText())
    }

    @Test
    fun `reading a missing file returns empty string`() = runBlocking {
        assertEquals("", repo.readText(File(tmp.root, "nope.txt").absolutePath))
    }

    @Test
    fun `large file beyond the guard is refused`() = runBlocking {
        val small = LocalFileContentRepository(changeBus = bus, maxBytes = 16)
        val path = File(tmp.root, "big.txt").absolutePath
        File(path).writeText("this is definitely more than sixteen bytes")

        val error = runCatching { small.readText(path) }.exceptionOrNull()

        assertTrue(error is FileTooLargeException)
        assertEquals(path, (error as FileTooLargeException).path)
    }

    @Test
    fun `lastModified reflects the file on disk`() = runBlocking {
        val path = File(tmp.root, "m.txt").absolutePath
        repo.writeText(path, "x")

        assertEquals(File(path).lastModified(), repo.lastModifiedMillis(path))
    }

    @Test
    fun `write emits CREATED for a new file and MODIFIED for an existing file`() = runBlocking {
        val path = File(tmp.root, "watched.txt").absolutePath

        val created = awaitFirst(repo.observeChanges()) { repo.writeText(path, "hi") }
        assertEquals(FileChangeType.CREATED, created.type)
        assertEquals(path, created.path)

        val modified = awaitFirst(repo.observeChanges()) { repo.writeText(path, "updated") }
        assertEquals(FileChangeType.MODIFIED, modified.type)
        assertEquals(path, modified.path)
    }
}

/** Subscribes to [flow], runs [action], and returns the first emitted value (fails after 2s). */
internal suspend fun <T> awaitFirst(flow: Flow<T>, action: suspend () -> Unit): T = coroutineScope {
    val deferred = async(Dispatchers.Unconfined) { flow.first() }
    yield()
    action()
    withTimeout(2000) { deferred.await() }
}
