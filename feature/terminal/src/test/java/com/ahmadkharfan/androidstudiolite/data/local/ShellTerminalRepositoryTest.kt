package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

class ShellTerminalRepositoryTest {

    private val hostShell = File("/bin/sh")

    private suspend fun ShellTerminalRepository.collectInto(sink: MutableList<TerminalEvent>): Job {
        val subscribed = CompletableDeferred<Unit>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            events.onSubscription { subscribed.complete(Unit) }.collect(sink::add)
        }
        subscribed.await()
        return job
    }

    @Test
    fun streamsRealOutputExpandsEnvAndReportsExitCodes() = runBlocking {
        assumeTrue("requires a POSIX /bin/sh on the host", hostShell.exists())
        val home = Files.createTempDirectory("asl-term-home").toFile()

        val repo = ShellTerminalRepository(
            shellPathProvider = { hostShell.absolutePath },
            environmentProvider = { mapOf("HOME" to home.absolutePath, "ASL_GREETING" to "greetings") },
            defaultWorkingDirectory = { home },
            ioDispatcher = Dispatchers.IO,
        )

        val events = Collections.synchronizedList(mutableListOf<TerminalEvent>())
        val collector = repo.collectInto(events)

        repo.start()
        repo.send("echo hello")
        repo.send("pwd")
        repo.send("echo \$ASL_GREETING")
        repo.send("(exit 7)")

        withTimeout(10_000) {
            while (events.count { it is TerminalEvent.CommandFinished } < 4) delay(20)
        }
        repo.stop()
        collector.cancel()

        val outputs = events.filterIsInstance<TerminalEvent.Output>().map { it.line.text }
        val exitCodes = events.filterIsInstance<TerminalEvent.CommandFinished>().map { it.exitCode }

        assertTrue("echo output should stream through: $outputs", outputs.contains("hello"))
        assertTrue("injected env var should expand: $outputs", outputs.contains("greetings"))
        assertTrue(
            "pwd should print the session working dir: $outputs",
            outputs.any { it.endsWith(home.name) },
        )

        assertTrue("sentinel must be swallowed: $outputs", outputs.none { it.contains("ASL_CMD_DONE") })
        assertEquals("exit codes must be bracketed per command", listOf(0, 0, 0, 7), exitCodes)
    }

    @Test
    fun cdPersistsAcrossCommandsInOneSession() = runBlocking {
        assumeTrue("requires a POSIX /bin/sh on the host", hostShell.exists())
        val home = Files.createTempDirectory("asl-term-cd").toFile()
        File(home, "sub").mkdirs()

        val repo = ShellTerminalRepository(
            shellPathProvider = { hostShell.absolutePath },
            environmentProvider = { emptyMap() },
            defaultWorkingDirectory = { home },
        )
        val events = Collections.synchronizedList(mutableListOf<TerminalEvent>())
        val collector = repo.collectInto(events)

        repo.start()
        repo.send("cd sub")
        repo.send("pwd")
        withTimeout(10_000) {
            while (events.count { it is TerminalEvent.CommandFinished } < 2) delay(20)
        }
        repo.stop()
        collector.cancel()

        val outputs = events.filterIsInstance<TerminalEvent.Output>().map { it.line.text }
        assertTrue("cd should carry over to the next command: $outputs", outputs.any { it.endsWith("sub") })
    }
}
