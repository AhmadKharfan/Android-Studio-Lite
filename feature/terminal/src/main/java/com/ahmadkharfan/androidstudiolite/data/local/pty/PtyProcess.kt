package com.ahmadkharfan.androidstudiolite.data.local.pty

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class PtyProcess private constructor(
    private val master: ParcelFileDescriptor,
    private val pid: Int,
) {
    val output: InputStream = FileInputStream(master.fileDescriptor)

    val input: OutputStream = FileOutputStream(master.fileDescriptor)

    @Volatile
    private var closed = false

    fun resize(rows: Int, cols: Int) {
        if (closed) return
        NativePty.nativeSetWinSize(master.fd, rows, cols)
    }

    fun waitFor(): Int = NativePty.nativeWaitFor(pid)

    fun destroy() {
        if (closed) return
        closed = true
        val fd = runCatching { master.detachFd() }.getOrDefault(-1)
        NativePty.nativeDestroy(fd, pid)
    }

    companion object {
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
