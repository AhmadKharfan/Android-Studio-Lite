package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.Response
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Serializes every outgoing message onto a single stream as one line of JSON. All writes are
 * synchronized so a build's streaming notifications never interleave mid-line with a response coming
 * from another thread. This is handed the process's *real* stdout, captured before [Main] redirects
 * `System.out` to stderr — so stray `println`s can't corrupt the protocol.
 */
class MessageWriter(rawOut: OutputStream) {

    private val out: BufferedWriter = BufferedWriter(OutputStreamWriter(rawOut, StandardCharsets.UTF_8))

    @Synchronized
    private fun send(line: String) {
        out.write(line)
        out.write("\n")
        out.flush()
    }

    fun respond(response: Response) = send(response.encode())

    fun emit(notification: Notification) = send(notification.encode())
}
