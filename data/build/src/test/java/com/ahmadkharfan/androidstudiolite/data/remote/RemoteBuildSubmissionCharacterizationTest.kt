package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteBuildSubmissionCharacterizationTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `zip build retains submission order and event sequence`() = runTest {
        val gateway = RecordingGateway()
        val request = request()

        val events = buildSystem(gateway).build(request).toList()

        assertEquals(
            listOf(
                BuildEvent.Started(request),
                BuildEvent.RemoteBuildBound(BUILD_ID),
                BuildEvent.Finished(success = true, durationMillis = 17),
            ),
            events,
        )
        assertEquals(listOf("create", "start", "stream"), gateway.calls)
        assertEquals("zip", gateway.createdRequest?.sourceType)
        assertNotNull(gateway.createdRequest?.sourceHash)
    }

    @Test
    fun `creation failure retains problem and failed finish sequence`() = runTest {
        val gateway = RecordingGateway(createFailure = IllegalStateException("broken"))
        val request = request()

        val events = buildSystem(gateway).build(request).toList()

        assertEquals(
            listOf(
                BuildEvent.Started(request),
                BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, "broken"),
                BuildEvent.Finished(success = false, durationMillis = 0),
            ),
            events,
        )
        assertEquals(listOf("create"), gateway.calls)
    }

    private fun request(): BuildRequest {
        val root = temporaryFolder.newFolder("project")
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"sample\"")
        return BuildRequest(root, ":app", "debug", operationId = "operation-1")
    }

    private fun buildSystem(gateway: RemoteBuildGateway) = RemoteBuildSystem(
        gateway = gateway,
        packager = ProjectPackager(),
        downloadArtifact = { _, _, _, _ -> error("Unexpected artifact download") },
        gradleReader = GradleProjectReader(),
        sourceDir = temporaryFolder.newFolder("sources"),
        clock = MonotonicClock { 0L },
    )

    private class RecordingGateway(
        private val createFailure: Throwable? = null,
    ) : RemoteBuildGateway {
        val calls = mutableListOf<String>()
        var createdRequest: CreateBuildRequest? = null

        override suspend fun createBuild(request: CreateBuildRequest): CreateBuildResponse {
            calls += "create"
            createdRequest = request
            createFailure?.let { throw it }
            return CreateBuildResponse(buildId = BUILD_ID, sourceUploadRequired = false)
        }

        override suspend fun startBuild(buildId: String) {
            calls += "start"
        }

        override suspend fun openStream(buildId: String, listener: WebSocketListener): WebSocket {
            calls += "stream"
            val socket = FakeWebSocket()
            listener.onMessage(socket, """{"type":"finished","success":true,"durationMillis":17}""")
            listener.onClosed(socket, 1000, "closed")
            return socket
        }

        override suspend fun requireSecureSigningTransport() = unexpected()
        override suspend fun uploadSource(uploadUrl: String, file: File, method: String) = unexpected()
        override suspend fun cancelBuild(buildId: String) = unexpected()
        override suspend fun buildStatus(buildId: String): BuildStatusResponse = unexpected()
        private fun unexpected(): Nothing = error("Unexpected gateway call")
    }

    private class FakeWebSocket : WebSocket {
        override fun request(): Request = Request.Builder().url("http://localhost/").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = false
        override fun send(bytes: ByteString): Boolean = false
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }

    private companion object {
        const val BUILD_ID = "build-1"
    }
}
