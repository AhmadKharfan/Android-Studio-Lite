package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteBuildStreamTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `finished stream emits parsed events and stops following`() = runTest {
        val gateway = FakeRemoteBuildGateway(
            statuses = listOf(runningStatus()),
            streams = listOf(
                listOf(
                    """{"type":"output","line":"compiling","stream":"STDOUT"}""",
                    """{"type":"finished","success":true,"durationMillis":42}""",
                ),
            ),
        )

        val events = buildSystem(gateway).attach(BUILD_ID, projectRoot()).toList()

        assertEquals(
            listOf(
                BuildEvent.RemoteBuildBound(BUILD_ID),
                BuildEvent.Progress("Building…"),
                BuildEvent.Output("compiling", BuildEvent.OutputStream.STDOUT),
                BuildEvent.Finished(success = true, durationMillis = 42),
            ),
            events,
        )
        assertEquals(1, gateway.cancelledSocketCount)
    }

    @Test
    fun `reconnected stream skips replayed frames and emits new frames`() = runTest {
        val replayedOutput = """{"type":"output","line":"first","stream":"STDOUT"}"""
        val gateway = FakeRemoteBuildGateway(
            statuses = listOf(runningStatus(), runningStatus()),
            streams = listOf(
                listOf(replayedOutput),
                listOf(
                    replayedOutput,
                    """{"type":"output","line":"second","stream":"STDOUT"}""",
                    """{"type":"finished","success":true,"durationMillis":73}""",
                ),
            ),
        )

        val events = buildSystem(gateway).attach(BUILD_ID, projectRoot()).toList()

        assertEquals(
            listOf(
                BuildEvent.RemoteBuildBound(BUILD_ID),
                BuildEvent.Progress("Building…"),
                BuildEvent.Output("first", BuildEvent.OutputStream.STDOUT),
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.INFO,
                    message = "Connection to build server lost (retrying…)",
                ),
                BuildEvent.Output("second", BuildEvent.OutputStream.STDOUT),
                BuildEvent.Finished(success = true, durationMillis = 73),
            ),
            events,
        )
        assertEquals(2, gateway.cancelledSocketCount)
    }

    @Test
    fun `disconnect followed by failed status emits the server failure`() = runTest {
        val gateway = FakeRemoteBuildGateway(
            statuses = listOf(
                runningStatus(),
                BuildStatusResponse(
                    buildId = BUILD_ID,
                    status = "FAILED",
                    durationMillis = 91,
                    errorMessage = "compile failed",
                ),
            ),
            streams = listOf(emptyList()),
        )

        val events = buildSystem(gateway).attach(BUILD_ID, projectRoot()).toList()

        assertEquals(
            listOf(
                BuildEvent.RemoteBuildBound(BUILD_ID),
                BuildEvent.Progress("Building…"),
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = "compile failed",
                ),
                BuildEvent.Finished(success = false, durationMillis = 91),
            ),
            events,
        )
        assertEquals(1, gateway.cancelledSocketCount)
    }

    private fun buildSystem(gateway: RemoteBuildGateway): RemoteBuildSystem {
        return RemoteBuildSystem(
            gateway = gateway,
            packager = ProjectPackager(),
            downloadArtifact = { _, _, _, _ -> error("Unexpected artifact download") },
            gradleReader = GradleProjectReader(),
            sourceDir = temporaryFolder.newFolder("sources"),
            clock = MonotonicClock { 0L },
        )
    }

    private fun projectRoot(): File = temporaryFolder.newFolder("project")

    private fun runningStatus() = BuildStatusResponse(buildId = BUILD_ID, status = "RUNNING")

    private class FakeRemoteBuildGateway(
        statuses: List<BuildStatusResponse>,
        streams: List<List<String>>,
    ) : RemoteBuildGateway {
        private val remainingStatuses = ArrayDeque(statuses)
        private val remainingStreams = ArrayDeque(streams)
        private val sockets = mutableListOf<FakeWebSocket>()

        val cancelledSocketCount: Int
            get() = sockets.count { it.isCancelled }

        override suspend fun buildStatus(buildId: String): BuildStatusResponse =
            remainingStatuses.removeFirst()

        override suspend fun openStream(buildId: String, listener: WebSocketListener): WebSocket {
            val socket = FakeWebSocket().also(sockets::add)
            remainingStreams.removeFirst().forEach { listener.onMessage(socket, it) }
            listener.onClosed(socket, 1000, "closed")
            return socket
        }

        override suspend fun requireSecureSigningTransport() = unexpectedCall()

        override suspend fun createBuild(request: CreateBuildRequest): CreateBuildResponse = unexpectedCall()

        override suspend fun uploadSource(uploadUrl: String, file: File, method: String) = unexpectedCall()

        override suspend fun startBuild(buildId: String) = unexpectedCall()

        override suspend fun cancelBuild(buildId: String) = unexpectedCall()

        private fun unexpectedCall(): Nothing = error("Unexpected remote build gateway call")
    }

    private class FakeWebSocket : WebSocket {
        var isCancelled = false
            private set

        override fun request(): Request = Request.Builder().url("http://localhost/").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = false
        override fun send(bytes: ByteString): Boolean = false
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() {
            isCancelled = true
        }
    }

    private companion object {
        const val BUILD_ID = "build-1"
    }
}
