package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildEventParser
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ProjectModelMapper
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteBuildSystem(
    private val client: RemoteClient,
    private val packager: ProjectPackager,
    private val artifactDownloader: ArtifactDownloader,
    private val gradleReader: GradleProjectReader,
    private val sourceDir: File,
    private val preferGitSource: suspend () -> Boolean = { false },
    private val gitSourceResolver: suspend (File) -> GitRemoteInfo? = { null },
    private val releaseSigningResolver: suspend () -> SigningConfig? = { null },
    private val encodeBase64: (ByteArray) -> String = { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
) : BuildSystem {

    private val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var currentBuildId: String? = null

    override suspend fun sync(projectRoot: File): ProjectModel {

        return runCatching {
            val wire = client.sync(
                CreateBuildRequest(
                    sourceType = "zip",
                    modulePath = ":app",
                    variant = "debug",
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
        val startedAt = System.currentTimeMillis()
        var socket: WebSocket? = null
        try {
            send(BuildEvent.Started(request))


            val gitSource = if (RemoteBuildRequestFactory.shouldUseGit(preferGitSource(), request)) {
                gitSourceResolver(projectRoot)
            } else {
                null
            }
            val signing = resolveSigning(request)


            val zip = if (gitSource == null) packager.packageProjectCached(projectRoot, sourceDir) else null


            val created = client.createBuild(
                RemoteBuildRequestFactory.create(
                    request = request,
                    gitSource = gitSource,
                    signing = signing,
                    sourceHash = zip?.let { packager.hashZip(it) },
                    projectKey = packager.projectKey(projectRoot),
                ),
            )
            currentBuildId = created.buildId
            send(BuildEvent.RemoteBuildBound(created.buildId))


            if (zip != null && created.sourceUploadRequired) {
                val uploadUrl = created.uploadUrl
                    ?: throw RemoteException(0, null, "Server returned no upload URL for a zip build")
                client.uploadSource(uploadUrl, zip, created.uploadMethod ?: "PUT")
            }


            client.startBuild(created.buildId)


            followBuildStream(
                buildId = created.buildId,
                projectRoot = projectRoot,
                parser = parser,
                startedAt = startedAt,
                socketHolder = { socket = it },
            )
        } catch (e: CancellationException) {

            throw e
        } catch (t: Throwable) {


            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = userFacingBuildError(t),
                ),
            )
            send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - startedAt))
        } finally {
            socket?.cancel()
            currentBuildId = null
        }
    }

    override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = channelFlow {
        val parser = BuildEventParser()
        val startedAt = System.currentTimeMillis()
        var socket: WebSocket? = null
        try {
            currentBuildId = buildId
            send(BuildEvent.RemoteBuildBound(buildId))


            val existing = runCatching { client.buildStatus(buildId) }.getOrNull()
            if (existing != null && isTerminalBuildStatus(existing.status)) {
                send(BuildEvent.Progress("Build finished — collecting results…"))
                emitTerminalFromStatus(buildId, existing, startedAt)
                return@channelFlow
            }
            send(BuildEvent.Progress(statusProgressLabel(existing?.status)))

            followBuildStream(
                buildId = buildId,
                projectRoot = projectRoot,
                parser = parser,
                startedAt = startedAt,
                socketHolder = { socket = it },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = userFacingBuildError(t),
                ),
            )
            send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - startedAt))
        } finally {
            socket?.cancel()
            currentBuildId = null
        }
    }

    private suspend fun ProducerScope<BuildEvent>.followBuildStream(
        buildId: String,
        projectRoot: File,
        parser: BuildEventParser,
        startedAt: Long,
        socketHolder: (WebSocket?) -> Unit,
    ) {
        var finishedSeen = false
        var reconnectAttempt = 0
        var processedTextFrames = 0
        val followDeadline = startedAt + BUILD_FOLLOW_TIMEOUT_MS

        while (!finishedSeen) {
            if (System.currentTimeMillis() >= followDeadline) break

            val frames = Channel<Frame>(Channel.UNLIMITED)
            socketHolder(null)
            val socket = client.openStream(buildId, FrameListener(frames))
            socketHolder(socket)

            val consumed = consumeBuildStream(
                buildId = buildId,
                projectRoot = projectRoot,
                parser = parser,
                frames = frames,
                startedAt = startedAt,
                followDeadline = followDeadline,
                replayPrefixToSkip = processedTextFrames,
            )
            finishedSeen = consumed.finished
            processedTextFrames = maxOf(processedTextFrames, consumed.textFramesSeen)
            frames.close()
            socket.cancel()
            socketHolder(null)

            if (finishedSeen) break

            val terminal = runCatching { client.buildStatus(buildId) }
                .getOrNull()
                ?.takeIf { isTerminalBuildStatus(it.status) }
            if (terminal != null) {
                emitTerminalFromStatus(
                    buildId = buildId,
                    status = terminal,
                    startedAt = startedAt,
                )
                finishedSeen = true
                break
            }

            if (System.currentTimeMillis() >= followDeadline) break

            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.INFO,
                    message = "Connection to build server lost (retrying…)",
                ),
            )
            val backoff = min(
                STREAM_RECONNECT_BACKOFF_CAP_MS,
                STREAM_RECONNECT_BACKOFF_MS shl min(reconnectAttempt, 5),
            )
            delay(backoff)
            reconnectAttempt++
        }


        if (!finishedSeen) {
            val polled = runCatching { client.buildStatus(buildId) }.getOrNull()
            if (polled != null && isTerminalBuildStatus(polled.status)) {
                emitTerminalFromStatus(
                    buildId = buildId,
                    status = polled,
                    startedAt = startedAt,
                )
            } else {
                val timedOut = System.currentTimeMillis() >= followDeadline
                if (timedOut) runCatching { client.cancelBuild(buildId) }
                send(
                    BuildEvent.Problem(
                        severity = BuildEvent.ProblemSeverity.ERROR,
                        message = if (timedOut) {
                            "Build is still running on the server but this session timed out waiting for updates. " +
                                "Re-open the project to reconnect, or tap Run to start a new build."
                        } else {
                            "Lost connection to the build server before the build finished. " +
                                "The build may still be running on the server — re-open the project to reconnect, " +
                                "or tap Run to try again."
                        },
                    ),
                )
                send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - startedAt))
            }
        }
    }

    private suspend fun ProducerScope<BuildEvent>.consumeBuildStream(
        buildId: String,
        projectRoot: File,
        parser: BuildEventParser,
        frames: ReceiveChannel<Frame>,
        startedAt: Long,
        followDeadline: Long,
        replayPrefixToSkip: Int,
    ): StreamConsumeResult {
        var textFramesSeen = 0
        while (true) {
            if (System.currentTimeMillis() >= followDeadline) {
                return StreamConsumeResult(false, textFramesSeen)
            }

            val result = withTimeoutOrNull(STATUS_POLL_INTERVAL_MS) {
                frames.receiveCatching()
            }
            if (result == null) {
                val polled = runCatching { client.buildStatus(buildId) }.getOrNull()
                if (polled != null && isTerminalBuildStatus(polled.status)) {
                    emitTerminalFromStatus(buildId, polled, startedAt)
                    return StreamConsumeResult(true, textFramesSeen)
                }
                send(BuildEvent.Progress(statusProgressLabel(polled?.status)))
                continue
            }
            if (result.isClosed) return StreamConsumeResult(false, textFramesSeen)
            when (val frame = result.getOrNull() ?: return StreamConsumeResult(false, textFramesSeen)) {
                is Frame.Text -> {
                    textFramesSeen++
                    if (textFramesSeen <= replayPrefixToSkip) continue
                    val event = parser.parse(frame.text, projectRoot) ?: continue
                    if (event is BuildEvent.ArtifactProduced) {
                        emitDownloadedArtifact(buildId, event)
                    } else {
                        send(event)
                    }
                    if (event is BuildEvent.Finished) return StreamConsumeResult(true, textFramesSeen)
                }
                is Frame.Closed, is Frame.Failure -> return StreamConsumeResult(false, textFramesSeen)
            }
        }
    }

    private fun statusProgressLabel(status: String?): String = when (status?.uppercase()) {
        null, "", "UNKNOWN" -> "Build still running…"
        "QUEUED" -> "Build queued…"
        "STARTING", "PREPARING" -> "Starting build…"
        "RUNNING" -> "Building…"
        "UPLOADING" -> "Uploading…"
        else -> "Build status: ${status.lowercase()}…"
    }

    private suspend fun ProducerScope<BuildEvent>.emitTerminalFromStatus(
        buildId: String,
        status: BuildStatusResponse,
        startedAt: Long,
    ) {
        val success = status.status.equals("SUCCEEDED", ignoreCase = true)
        if (success) {
            val downloaded = runCatching {
                artifactDownloader.download(buildId, fallbackName = null)
            }.getOrNull()
            if (downloaded != null) {
                send(BuildEvent.ArtifactProduced(downloaded.file, downloaded.kind))
            }
        } else {
            val raw = status.error?.message ?: "Build ${status.status.lowercase()}"
            val message = when {
                raw.contains("OOM", ignoreCase = true) ||
                    raw.contains("evict", ignoreCase = true) ||
                    raw.contains("out of memory", ignoreCase = true) ->
                    "Build worker ran out of memory and was stopped. Tap Run to try again."
                else -> raw
            }
            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = message,
                ),
            )
        }
        send(
            BuildEvent.Finished(
                success = success,
                durationMillis = status.durationMillis ?: (System.currentTimeMillis() - startedAt),
            ),
        )
    }

    private fun isTerminalBuildStatus(status: String): Boolean =
        status.equals("SUCCEEDED", ignoreCase = true) ||
            status.equals("FAILED", ignoreCase = true) ||
            status.equals("TIMED_OUT", ignoreCase = true) ||
            status.equals("CANCELED", ignoreCase = true) ||
            status.equals("CANCELLED", ignoreCase = true) ||
            status.equals("ERROR", ignoreCase = true)

    private fun userFacingBuildError(t: Throwable): String {
        for (err in generateSequence(t) { it.cause }) {
            if (err is RemoteException) {
                val code = err.code?.lowercase().orEmpty()
                val msg = err.message.takeIf { it.isNotBlank() && !it.matches(Regex("""HTTP \d+""")) }
                when {
                    code == "quota_exceeded" || "quota" in err.message.lowercase() ||
                        "build time" in err.message.lowercase() ->
                        return msg
                            ?: "You've used today's build time for this device. Quota resets at midnight UTC — try again tomorrow."
                    err.httpStatus == 429 || code == "rate_limited" ->
                        return msg
                            ?: "Too many builds right now — wait a moment and try again."
                }
            }
        }

        val chain = generateSequence(t) { it.cause }.toList()
        for (err in chain) {
            when (err) {
                is java.net.UnknownHostException ->
                    return "You're offline or DNS failed — check your internet connection and try again."
                is java.net.ConnectException ->
                    return "Can't reach the build server — check your internet connection and try again."
                is java.net.NoRouteToHostException ->
                    return "No network route to the build server — check your internet connection."
                is java.net.SocketTimeoutException ->
                    return "Build server timed out — check your internet connection and try again."
            }
        }
        val message = chain.mapNotNull { it.message?.takeIf(String::isNotBlank) }.firstOrNull().orEmpty()
        val lower = message.lowercase()
        return when {
            "unable to resolve host" in lower ||
                "failed to connect" in lower ||
                "network is unreachable" in lower ||
                "software caused connection abort" in lower ||
                "connection refused" in lower ||
                lower == "network error" ->
                "You're offline or can't reach the build server — check your internet connection and try again."
            message.contains("PROTOCOL_ERROR", ignoreCase = true) ||
                message.contains("stream was reset", ignoreCase = true) ->
                "Connection to the build server was interrupted — check your internet and try again."
            message.isNotBlank() -> message
            else -> "Build failed — check your internet connection and try again."
        }
    }

    private suspend fun ProducerScope<BuildEvent>.emitDownloadedArtifact(
        buildId: String,
        wireEvent: BuildEvent.ArtifactProduced,
    ) {
        send(BuildEvent.Progress("Downloading ${wireEvent.file.name}…"))
        val downloaded = runCatching {
            artifactDownloader.download(buildId, fallbackName = wireEvent.file.name)
        }.onFailure { err ->
            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = "Couldn't download the APK: ${err.message ?: err.javaClass.simpleName}",
                ),
            )
        }.getOrNull()
        if (downloaded != null) {
            send(BuildEvent.ArtifactProduced(downloaded.file, downloaded.kind))
        }


    }

    override fun cancel() {
        val id = currentBuildId ?: return
        cancelScope.launch { runCatching { client.cancelBuild(id) } }
    }

    private suspend fun resolveSigning(request: BuildRequest) =
        if (RemoteBuildRequestFactory.isReleaseVariant(request.variantName)) {
            RemoteBuildRequestFactory.releaseSigningMaterial(
                config = releaseSigningResolver(),
                encodeBase64 = encodeBase64,
            )
        } else {
            null
        }

    private sealed interface Frame {
        data class Text(val text: String) : Frame
        data object Closed : Frame
        data class Failure(val error: Throwable) : Frame
    }

    private data class StreamConsumeResult(val finished: Boolean, val textFramesSeen: Int)

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

    private companion object {
        private const val BUILD_FOLLOW_TIMEOUT_MS = 1_800_000L
        private const val STREAM_RECONNECT_BACKOFF_MS = 1_000L
        private const val STREAM_RECONNECT_BACKOFF_CAP_MS = 30_000L
        private const val STATUS_POLL_INTERVAL_MS = 5_000L
    }
}
