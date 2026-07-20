package com.ahmadkharfan.androidstudiolite.data.local.pty

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The minimal surface [PtyTerminalRepository][com.ahmadkharfan.androidstudiolite.data.local.pty.PtyTerminalRepository]
 * needs from a live PTY. Abstracted behind an interface so the repository's read loop, input plumbing
 * and resize handling can be unit-tested on the JVM with an in-memory fake, without loading the native
 * library or spawning a process.
 */
interface PtySession {
    val output: InputStream
    val input: OutputStream
    fun resize(rows: Int, cols: Int)
    fun waitFor(): Int
    fun destroy()
}

/** Opens a real PTY-backed session. Swapped for a fake in tests. */
fun interface PtySessionFactory {
    fun open(command: List<String>, environment: Map<String, String>, workingDir: File?, rows: Int, cols: Int): PtySession
}

/** Production factory: forks a real shell via [PtyProcess]. */
object RealPtySessionFactory : PtySessionFactory {
    override fun open(
        command: List<String>,
        environment: Map<String, String>,
        workingDir: File?,
        rows: Int,
        cols: Int,
    ): PtySession {
        val process = PtyProcess.start(command, environment, workingDir, rows, cols)
        return object : PtySession {
            override val output: InputStream get() = process.output
            override val input: OutputStream get() = process.input
            override fun resize(rows: Int, cols: Int) = process.resize(rows, cols)
            override fun waitFor(): Int = process.waitFor()
            override fun destroy() = process.destroy()
        }
    }
}
