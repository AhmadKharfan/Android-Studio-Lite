package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.Incoming
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.Request
import com.ahmadkharfan.androidstudiolite.tooling.proto.Response
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * A minimal JSON-RPC client over a spawned tooling-server process, used only by the desktop harness.
 * It mirrors what the app's real client does: write newline-delimited requests to the process's stdin,
 * read responses/notifications off its stdout on a background thread, correlate responses by id, and
 * collect streamed events. The real Android client ([core/tooling]) reuses the same protocol types.
 */
class JsonRpcTestClient private constructor(private val process: Process) : AutoCloseable {

    private val writer: BufferedWriter =
        BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableFuture<Response>>()

    /** Every event the server streamed, in arrival order. */
    val events = CopyOnWriteArrayList<Notification>()

    private val reader = Thread {
        BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { input ->
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) continue
                when (val incoming = runCatching { Incoming.parse(line) }.getOrNull()) {
                    is Incoming.Resp -> pending.remove(incoming.response.id)?.complete(incoming.response)
                    is Incoming.Event -> events += incoming.notification
                    else -> {}
                }
            }
        }
        // Stream closed: fail anything still waiting so tests don't hang.
        pending.values.forEach { it.completeExceptionally(IllegalStateException("server stdout closed")) }
    }.apply { isDaemon = true; start() }

    fun call(method: String, params: JsonValue.Obj, timeoutSeconds: Long = 120): Response {
        val id = nextId.getAndIncrement()
        val future = CompletableFuture<Response>()
        pending[id] = future
        send(Request(id, method, params).encode())
        return future.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    private fun send(line: String) {
        synchronized(writer) {
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }
    }

    /** Simulates a client crash: slam stdin shut without a graceful shutdown. */
    fun disconnectAbruptly() {
        runCatching { process.outputStream.close() }
    }

    fun waitForExit(timeoutSeconds: Long): Boolean = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

    override fun close() {
        runCatching { process.outputStream.close() }
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    companion object {
        /** Spawns `java -jar <serverJar>` and returns a connected client. */
        fun spawn(serverJar: File): JsonRpcTestClient {
            val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
            val process = ProcessBuilder(javaBin, "-jar", serverJar.absolutePath)
                .redirectErrorStream(false) // keep stderr separate; stdout is the protocol
                .start()
            // Drain stderr so the process never blocks on a full pipe, and surface it for debugging.
            Thread {
                process.errorStream.bufferedReader().forEachLine { System.err.println("[server] $it") }
            }.apply { isDaemon = true; start() }
            return JsonRpcTestClient(process)
        }
    }
}
