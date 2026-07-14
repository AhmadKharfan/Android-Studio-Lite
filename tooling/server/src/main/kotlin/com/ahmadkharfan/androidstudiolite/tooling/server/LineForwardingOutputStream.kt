package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.Events
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Adapts Gradle's stdout/stderr (which the Tooling API writes to as an [OutputStream]) into
 * line-oriented `log` events. Bytes are accumulated and flushed one line at a time so the client
 * sees whole log lines, not arbitrary byte chunks. Writes arrive on the Tooling API's reader thread,
 * so accumulation is synchronized.
 */
class LineForwardingOutputStream(
    private val stream: String,
    private val emit: (Notification) -> Unit,
) : OutputStream() {

    private val buffer = StringBuilder()

    @Synchronized
    override fun write(b: Int) {
        if (b == '\n'.code) flushLine() else if (b != '\r'.code) buffer.append(b.toChar())
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val text = String(b, off, len, StandardCharsets.UTF_8)
        for (c in text) {
            if (c == '\n') flushLine() else if (c != '\r') buffer.append(c)
        }
    }

    @Synchronized
    override fun flush() {
        // Gradle may flush a partial line (e.g. a progress bar without a newline); surface it so the
        // client isn't left waiting, then keep accumulating from empty.
        if (buffer.isNotEmpty()) flushLine()
    }

    @Synchronized
    override fun close() {
        if (buffer.isNotEmpty()) flushLine()
    }

    private fun flushLine() {
        val line = buffer.toString()
        buffer.setLength(0)
        emit(Events.log(stream, line))
    }
}
