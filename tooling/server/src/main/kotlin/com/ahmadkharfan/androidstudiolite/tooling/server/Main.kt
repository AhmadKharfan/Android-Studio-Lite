package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.Incoming
import com.ahmadkharfan.androidstudiolite.tooling.proto.ToolingProto
import java.io.BufferedReader
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Entry point of the on-device Gradle tooling server. The app extracts this module's fat jar from its
 * full-flavor assets and spawns it with the installed JDK; the two speak the :tooling:proto JSON-RPC
 * protocol over stdio — newline-delimited JSON, one object per line.
 *
 * stdout is reserved exclusively for the protocol, so before anything else we grab the real stdout for
 * the [MessageWriter] and redirect `System.out` to stderr; that way any stray `println` (ours or a
 * library's) can never corrupt the stream. The read loop runs on the main thread and stays responsive
 * to `cancel` while a build runs on the service's worker thread. EOF on stdin means the client went
 * away — we shut the daemons down and exit.
 */
fun main() {
    // Capture the true stdout for the protocol, then send System.out to stderr.
    val protocolOut = FileOutputStream(FileDescriptor.out)
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8"))

    val writer = MessageWriter(protocolOut)
    val service = ToolingService(writer)

    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    try {
        while (true) {
            val line = reader.readLine() ?: break // client disconnected
            if (line.isBlank()) continue
            val incoming = runCatching { Incoming.parse(line) }.getOrElse {
                System.err.println("asl-tooling: dropping unparseable message: $it")
                continue
            }
            when (incoming) {
                is Incoming.Req -> {
                    service.handle(incoming.request)
                    // `shutdown` is handled synchronously (response already sent); leave the read loop
                    // so the process can exit instead of blocking on the next stdin line.
                    if (incoming.request.method == ToolingProto.Method.SHUTDOWN) break
                }
                // The server never receives responses or events; ignore anything unexpected.
                else -> System.err.println("asl-tooling: ignoring unexpected message on server input")
            }
        }
    } finally {
        // Reached on EOF (client crash/disconnect) or an unexpected read error: stop the daemons so we
        // don't leak a Gradle process, then let the JVM exit.
        service.shutdown()
    }
}
