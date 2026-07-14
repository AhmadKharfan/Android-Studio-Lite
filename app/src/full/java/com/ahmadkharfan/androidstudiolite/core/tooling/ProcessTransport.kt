package com.ahmadkharfan.androidstudiolite.core.tooling

import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Owns the spawned tooling-server OS process and its stdio. Runs inside the `:tooling` service process.
 * stdout is read line by line (each line a JSON-RPC response or event) and handed to [onLine]; stdin is
 * written with request lines; stderr is logged. When the process exits, [onExit] fires with the code so
 * the service can decide whether to restart.
 */
class ProcessTransport(
    private val command: List<String>,
    private val environment: Map<String, String>,
    private val workingDir: File,
) {

    @Volatile private var process: Process? = null
    @Volatile private var stdin: BufferedWriter? = null

    var onLine: ((String) -> Unit)? = null
    var onExit: ((Int) -> Unit)? = null

    val isAlive: Boolean get() = process?.isAlive == true

    fun start() {
        val builder = ProcessBuilder(command).directory(workingDir.takeIf { it.exists() })
        builder.environment().putAll(environment)
        val proc = builder.start()
        process = proc
        stdin = BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8))

        thread(name = "tooling-server-stdout", isDaemon = true) {
            runCatching {
                proc.inputStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { onLine?.invoke(it) }
            }
            val code = runCatching { proc.waitFor() }.getOrDefault(-1)
            onExit?.invoke(code)
        }
        thread(name = "tooling-server-stderr", isDaemon = true) {
            runCatching {
                proc.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { Log.d(TAG, it) }
            }
        }
    }

    fun writeLine(line: String) {
        val writer = stdin ?: return
        synchronized(this) {
            runCatching {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }.onFailure { Log.w(TAG, "failed to write to tooling server", it) }
        }
    }

    fun stop() {
        runCatching { stdin?.close() }
        process?.destroy()
        process = null
        stdin = null
    }

    private companion object {
        const val TAG = "tooling-server"
    }
}
