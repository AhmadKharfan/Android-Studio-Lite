package com.ahmadkharfan.androidstudiolite.data.local.pty

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A live pseudo-terminal session: a child shell running behind a master PTY fd. Unlike the
 * line-oriented [ShellTerminalRepository][com.ahmadkharfan.androidstudiolite.data.local.ShellTerminalRepository],
 * this is a full TTY — the child sees a terminal, so interactive and curses programs work, and the
 * app can resize it (SIGWINCH) as the view changes.
 *
 * [output] is the child's terminal output (feed it to the emulator); [input] carries the user's
 * keystrokes to the child. Both wrap the single bidirectional master fd, adopted into a
 * [ParcelFileDescriptor] so the Android runtime owns its lifetime.
 */
class PtyProcess private constructor(
    private val master: ParcelFileDescriptor,
    private val pid: Int,
) {
    /** Child → app: raw terminal output bytes. */
    val output: InputStream = FileInputStream(master.fileDescriptor)

    /** App → child: keystrokes / pasted text (already encoded). */
    val input: OutputStream = FileOutputStream(master.fileDescriptor)

    @Volatile
    private var closed = false

    /** Tell the child the terminal is now [rows]×[cols]; harmless if already closed. */
    fun resize(rows: Int, cols: Int) {
        if (closed) return
        NativePty.nativeSetWinSize(master.fd, rows, cols)
    }

    /** Block until the shell exits and return its exit code (128+signal if killed). */
    fun waitFor(): Int = NativePty.nativeWaitFor(pid)

    /** SIGHUP the child and release the fd. Idempotent. */
    fun destroy() {
        if (closed) return
        closed = true
        try {
            NativePty.nativeDestroy(master.fd, pid)
        } finally {
            runCatching { master.close() }
        }
    }

    companion object {
        /**
         * Fork [command] (argv[0] must be an absolute path) under a [rows]×[cols] PTY, in [workingDir]
         * with [environment] as its whole environment. Throws [IOException] if the fork fails.
         */
        fun start(
            command: List<String>,
            environment: Map<String, String>,
            workingDir: File?,
            rows: Int,
            cols: Int,
        ): PtyProcess {
            require(command.isNotEmpty()) { "command must not be empty" }
            NativePty.ensureLoaded()
            val argv = command.toTypedArray()
            val envp = environment.map { "${it.key}=${it.value}" }.toTypedArray()
            val fds = NativePty.nativeForkPty(argv, envp, workingDir?.absolutePath, rows.coerceAtLeast(1), cols.coerceAtLeast(1))
                ?: throw IOException("forkpty failed for ${command.first()}")
            val master = ParcelFileDescriptor.adoptFd(fds[0])
            return PtyProcess(master, fds[1])
        }
    }
}
