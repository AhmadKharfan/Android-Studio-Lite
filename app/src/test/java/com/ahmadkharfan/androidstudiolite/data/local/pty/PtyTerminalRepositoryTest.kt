package com.ahmadkharfan.androidstudiolite.data.local.pty

import com.ahmadkharfan.androidstudiolite.domain.model.TerminalEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

class PtyTerminalRepositoryTest {

    /** An in-memory PTY: [emit] simulates child output; [captured] records what the app typed. */
    private class FakePty : PtySession {
        private val pipe = PipedInputStream(64 * 1024)
        private val childOut = PipedOutputStream(pipe)
        val captured = ByteArrayOutputStream()
        var lastResize: Pair<Int, Int>? = null
        var destroyed = false

        override val output: InputStream get() = pipe
        override val input: OutputStream get() = captured
        override fun resize(rows: Int, cols: Int) { lastResize = rows to cols }
        override fun waitFor(): Int = 0
        override fun destroy() { destroyed = true; runCatching { childOut.close() } }

        fun emit(text: String) {
            childOut.write(text.toByteArray(StandardCharsets.UTF_8))
            childOut.flush()
        }
    }

    private fun repoWith(fake: FakePty) = PtyTerminalRepository(
        shellCommandProvider = { listOf("/system/bin/sh") },
        environmentProvider = { mapOf("TERM" to "xterm-256color") },
        defaultWorkingDirectory = { null },
        sessionFactory = PtySessionFactory { _, _, _, _, _ -> fake },
        ioDispatcher = Dispatchers.IO,
    )

    @Test
    fun streams_raw_child_output_as_byte_events() = runBlocking {
        val fake = FakePty()
        val repo = repoWith(fake)
        val events = Channel<TerminalEvent>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) { repo.events.collect { events.send(it) } }
        // Give the collector a moment to actually subscribe (SharedFlow has no replay).
        repeat(20) { yield(); Thread.sleep(5) }

        repo.start(rows = 24, cols = 80)
        fake.emit("top - 15:04:01[31mLOAD[0m")

        val bytes = withTimeout(3000) {
            var found: String? = null
            while (found == null) {
                val e = events.receive()
                if (e is TerminalEvent.Bytes) found = e.text
            }
            found
        }
        assertTrue("raw control sequences preserved", bytes!!.contains("[31m"))
        assertTrue(bytes.contains("top - 15:04:01"))
        collector.cancel()
        repo.stop()
    }

    @Test
    fun writeInput_reaches_the_child_verbatim() = runBlocking {
        val fake = FakePty()
        val repo = repoWith(fake)
        repo.start(rows = 24, cols = 80)
        repo.writeInput("q") // e.g. quit key for top — no newline appended
        repo.send("ls -la") // convenience: adds the Enter
        assertEquals("qls -la\n", fake.captured.toString(Charsets.UTF_8.name()))
        repo.stop()
    }

    @Test
    fun resize_is_forwarded_to_the_pty() = runBlocking {
        val fake = FakePty()
        val repo = repoWith(fake)
        repo.start(rows = 24, cols = 80)
        repo.resize(40, 120)
        assertEquals(40 to 120, fake.lastResize)
        repo.stop()
        assertTrue(fake.destroyed)
    }

    @Test
    fun session_end_emitted_when_child_output_closes() = runBlocking {
        val fake = FakePty()
        val repo = repoWith(fake)
        val events = Channel<TerminalEvent>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) { repo.events.collect { events.send(it) } }
        repeat(20) { yield(); Thread.sleep(5) }

        repo.start(rows = 24, cols = 80)
        fake.emit("bye")
        fake.destroy() // closes the child's output stream -> EOF on the read loop

        val ended = withTimeout(3000) {
            var sawEnd = false
            while (!sawEnd) {
                if (events.receive() is TerminalEvent.SessionEnded) sawEnd = true
            }
            true
        }
        assertTrue(ended)
        collector.cancel()
    }
}
