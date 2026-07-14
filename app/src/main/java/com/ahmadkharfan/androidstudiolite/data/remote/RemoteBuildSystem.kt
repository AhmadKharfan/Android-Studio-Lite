package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildEventParser
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ProjectModelMapper
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The production [BuildSystem]: runs builds on the server-side backend and streams the results into
 * the existing build console. A `build()` packages the project, uploads it, starts the build, and
 * relays the control-plane WebSocket's [BuildEvent] stream — downloading the produced artifact so the
 * unchanged install/run flow finds a real APK on disk. `cancel()` posts to the cancel endpoint;
 * `sync()` calls the server sync (falling back to the on-device static parser until it ships).
 *
 * Bound in place of the temporary `FakeBuildSystem` in the Koin graph (A2).
 */
class RemoteBuildSystem(
    private val client: RemoteClient,
    private val packager: ProjectPackager,
    private val artifactDownloader: ArtifactDownloader,
    private val gradleReader: GradleProjectReader,
    /** Scratch dir for source zips (e.g. `context.cacheDir/build-sources`). */
    private val sourceDir: File,
) : BuildSystem {

    /** Fire-and-forget scope for the cancel POST, whose lifetime is independent of the build flow. */
    private val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The id of the in-flight build, so [cancel] knows what to cancel. */
    @Volatile private var currentBuildId: String? = null

    override suspend fun sync(projectRoot: File): ProjectModel {
        // Server-side sync (Phase 3). Until /v1/sync ships, fall back to the on-device static parse.
        return runCatching {
            val wire = client.sync(
                CreateBuildRequest(
                    sourceType = "zip",
                    modulePath = ":app",
                    variantName = "debug",
                    kind = BuildKind.ASSEMBLE.name,
                ),
            )
            ProjectModelMapper.toDomain(wire, projectRoot)
        }.getOrElse {
            gradleReader.read(projectRoot).model
        }
    }

    override fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val projectRoot = request.projectRoot
        val parser = BuildEventParser()
        var zip: File? = null
        var socket: WebSocket? = null
        try {
            // 1. Package the project (excludes build/.gradle/.idea/.git).
            zip = File(sourceDir.apply { mkdirs() }, "src-${System.currentTimeMillis()}.zip")
            packager.packageProject(projectRoot, zip)

            // 2. Create the build; upload the source zip to the presigned URL.
            val created = client.createBuild(
                CreateBuildRequest(
                    sourceType = "zip",
                    modulePath = request.modulePath,
                    variantName = request.variantName,
                    kind = request.kind.name,
                    tasks = defaultTasks(request),
                ),
            )
            currentBuildId = created.buildId
            val uploadUrl = created.uploadUrl
                ?: throw RemoteException(0, null, "Server returned no upload URL for a zip build")
            client.uploadSource(uploadUrl, zip, created.uploadMethod ?: "PUT")

            // 3. Start the build.
            client.startBuild(created.buildId)

            // 4. Stream BuildEvents over the WebSocket. Frames are drained off the OkHttp callback
            //    thread into this channel so they're processed sequentially — an artifact download
            //    (and its ArtifactProduced) then completes before Finished terminates the flow.
            val frames = Channel<Frame>(Channel.UNLIMITED)
            socket = client.openStream(created.buildId, FrameListener(frames))

            for (frame in frames) {
                when (frame) {
                    is Frame.Text -> {
                        val event = parser.parse(frame.text, projectRoot) ?: continue
                        if (event is BuildEvent.ArtifactProduced) {
                            emitDownloadedArtifact(created.buildId, event)
                        } else {
                            send(event)
                        }
                        if (event is BuildEvent.Finished) break
                    }
                    is Frame.Closed -> break
                    is Frame.Failure -> throw frame.error
                }
            }
        } finally {
            socket?.cancel()
            zip?.delete()
            currentBuildId = null
        }
    }

    /** Downloads the produced artifact and emits an [BuildEvent.ArtifactProduced] with the local file. */
    private suspend fun ProducerScope<BuildEvent>.emitDownloadedArtifact(
        buildId: String,
        wireEvent: BuildEvent.ArtifactProduced,
    ) {
        val downloaded = runCatching {
            artifactDownloader.download(buildId, fallbackName = wireEvent.file.name)
        }.getOrNull()
        if (downloaded != null) {
            send(BuildEvent.ArtifactProduced(downloaded.file, downloaded.kind))
        } else {
            // Keep the event (its name/kind) even if the download failed, so the UI still records it.
            send(wireEvent)
        }
    }

    override fun cancel() {
        val id = currentBuildId ?: return
        cancelScope.launch { runCatching { client.cancelBuild(id) } }
    }

    private fun defaultTasks(request: BuildRequest): List<String> {
        val variant = request.variantName.replaceFirstChar { it.uppercase() }
        return when (request.kind) {
            BuildKind.ASSEMBLE -> listOf("assemble$variant")
            BuildKind.BUNDLE -> listOf("bundle$variant")
            BuildKind.CLEAN -> listOf("clean")
        }
    }

    /** Frames forwarded off the OkHttp WebSocket thread into the sequential build-flow consumer. */
    private sealed interface Frame {
        data class Text(val text: String) : Frame
        data object Closed : Frame
        data class Failure(val error: Throwable) : Frame
    }

    private class FrameListener(private val frames: Channel<Frame>) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            frames.trySend(Frame.Text(text))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            frames.trySend(Frame.Closed)
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            frames.trySend(Frame.Closed)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            frames.trySend(Frame.Failure(t))
        }

        private companion object {
            const val NORMAL_CLOSURE = 1000
        }
    }
}
