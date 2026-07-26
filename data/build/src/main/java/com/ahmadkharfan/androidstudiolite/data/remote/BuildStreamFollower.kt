package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildEventParser
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class BuildStreamFollower(
    private val gateway: RemoteBuildGateway,
    private val downloadArtifact: suspend (
        buildId: String,
        fallbackName: String?,
        expectedSizeBytes: Long?,
        expectedSha256: String?,
    ) -> ArtifactDownloader.DownloadedArtifact?,
    private val clock: MonotonicClock,
) {
    suspend fun follow(
        request: BuildStreamRequest,
        socketHolder: (WebSocket?) -> Unit,
        emit: suspend (BuildEvent) -> Unit,
    ) {
        val state = FollowState()
        val deadline = request.startedAt + BUILD_FOLLOW_TIMEOUT_MS

        while (!state.finished && clock.elapsedMillis() < deadline) {
            val consumed = consumeConnection(request, deadline, state.processedTextFrames, socketHolder, emit)
            state.finished = consumed.finished
            state.processedTextFrames = maxOf(state.processedTextFrames, consumed.textFramesSeen)
            if (state.finished || emitTerminalStatusIfAvailable(request, emit)) {
                state.finished = true
                break
            }
            if (clock.elapsedMillis() >= deadline) break

            emit(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.INFO,
                    message = "Connection to build server lost (retrying…)",
                ),
            )
            val remaining = deadline - clock.elapsedMillis()
            delay(boundedWaitMillis(reconnectBackoffMillis(state.reconnectAttempt), remaining).milliseconds)
            state.reconnectAttempt++
        }

        if (!state.finished) emitIncompleteResult(request, deadline, emit)
    }

    suspend fun emitTerminalStatus(
        buildId: String,
        status: BuildStatusResponse,
        startedAt: Long,
        emit: suspend (BuildEvent) -> Unit,
    ) {
        val success = status.status.equals("SUCCEEDED", ignoreCase = true)
        if (success) {
            val downloaded = runCatching { downloadArtifact(buildId, null, null, null) }.getOrNull()
            if (downloaded != null) emit(BuildEvent.ArtifactProduced(downloaded.file, downloaded.kind))
        } else {
            emit(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = terminalFailureMessage(status),
                ),
            )
        }
        emit(
            BuildEvent.Finished(
                success = success,
                durationMillis = status.durationMillis ?: (clock.elapsedMillis() - startedAt),
            ),
        )
    }

    fun statusProgressLabel(status: String?): String = when (status?.uppercase()) {
        null, "", "UNKNOWN" -> "Build still running…"
        "QUEUED" -> "Build queued…"
        "STARTING", "PREPARING" -> "Starting build…"
        "RUNNING" -> "Building…"
        "SIGNING" -> "Signing and verifying release artifact…"
        "UPLOADING" -> "Uploading…"
        else -> "Build status: ${status.lowercase()}…"
    }

    fun isTerminalStatus(status: String): Boolean =
        status.equals("SUCCEEDED", ignoreCase = true) ||
            status.equals("FAILED", ignoreCase = true) ||
            status.equals("TIMED_OUT", ignoreCase = true) ||
            status.equals("CANCELED", ignoreCase = true) ||
            status.equals("CANCELLED", ignoreCase = true) ||
            status.equals("ERROR", ignoreCase = true)

    private suspend fun consumeConnection(
        request: BuildStreamRequest,
        deadline: Long,
        replayPrefixToSkip: Int,
        socketHolder: (WebSocket?) -> Unit,
        emit: suspend (BuildEvent) -> Unit,
    ): StreamConsumeResult {
        val frames = Channel<Frame>(Channel.UNLIMITED)
        socketHolder(null)
        val socket = gateway.openStream(request.buildId, FrameListener(frames))
        socketHolder(socket)
        val consumed = consumeFrames(request, frames, deadline, replayPrefixToSkip, emit)
        frames.close()
        socket.cancel()
        socketHolder(null)
        return consumed
    }

    private suspend fun consumeFrames(
        request: BuildStreamRequest,
        frames: ReceiveChannel<Frame>,
        deadline: Long,
        replayPrefixToSkip: Int,
        emit: suspend (BuildEvent) -> Unit,
    ): StreamConsumeResult {
        var textFramesSeen = 0
        while (clock.elapsedMillis() < deadline) {
            val remaining = deadline - clock.elapsedMillis()
            val result = withTimeoutOrNull(boundedWaitMillis(STATUS_POLL_INTERVAL_MS, remaining).milliseconds) {
                frames.receiveCatching()
            }
            if (result == null) {
                if (emitPolledStatusIfTerminal(request, emit)) {
                    return StreamConsumeResult(true, textFramesSeen)
                }
                continue
            }
            if (result.isClosed) return StreamConsumeResult(false, textFramesSeen)
            when (val frame = result.getOrNull() ?: return StreamConsumeResult(false, textFramesSeen)) {
                is Frame.Text -> {
                    textFramesSeen++
                    if (textFramesSeen <= replayPrefixToSkip) continue
                    val event = request.parser.parse(frame.text, request.projectRoot) ?: continue
                    if (emitParsedEvent(request, event, emit)) return StreamConsumeResult(true, textFramesSeen)
                }
                is Frame.Closed, is Frame.Failure -> return StreamConsumeResult(false, textFramesSeen)
            }
        }
        return StreamConsumeResult(false, textFramesSeen)
    }

    private suspend fun emitPolledStatusIfTerminal(
        request: BuildStreamRequest,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        val polled = runCatching { gateway.buildStatus(request.buildId) }.getOrNull()
        if (polled != null && isTerminalStatus(polled.status)) {
            emitTerminalStatus(request.buildId, polled, request.startedAt, emit)
            return true
        }
        emit(BuildEvent.Progress(statusProgressLabel(polled?.status)))
        return false
    }

    private suspend fun emitParsedEvent(
        request: BuildStreamRequest,
        event: BuildEvent,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        if (event is BuildEvent.ArtifactProduced) {
            if (!emitDownloadedArtifact(request.buildId, event, emit)) {
                emit(BuildEvent.Finished(false, clock.elapsedMillis() - request.startedAt))
                return true
            }
        } else {
            emit(event)
        }
        return event is BuildEvent.Finished
    }

    private suspend fun emitTerminalStatusIfAvailable(
        request: BuildStreamRequest,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        val terminal = runCatching { gateway.buildStatus(request.buildId) }
            .getOrNull()
            ?.takeIf { isTerminalStatus(it.status) }
            ?: return false
        emitTerminalStatus(request.buildId, terminal, request.startedAt, emit)
        return true
    }

    private suspend fun emitIncompleteResult(
        request: BuildStreamRequest,
        deadline: Long,
        emit: suspend (BuildEvent) -> Unit,
    ) {
        val polled = runCatching { gateway.buildStatus(request.buildId) }.getOrNull()
        if (polled != null && isTerminalStatus(polled.status)) {
            emitTerminalStatus(request.buildId, polled, request.startedAt, emit)
            return
        }
        val timedOut = clock.elapsedMillis() >= deadline
        if (timedOut) runCatching { gateway.cancelBuild(request.buildId) }
        emit(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, incompleteMessage(timedOut)))
        emit(BuildEvent.Finished(success = false, durationMillis = clock.elapsedMillis() - request.startedAt))
    }

    private suspend fun emitDownloadedArtifact(
        buildId: String,
        wireEvent: BuildEvent.ArtifactProduced,
        emit: suspend (BuildEvent) -> Unit,
    ): Boolean {
        emit(BuildEvent.Progress("Downloading ${wireEvent.file.name}…"))
        val downloaded = runCatching {
            downloadArtifact(buildId, wireEvent.file.name, wireEvent.sizeBytes, wireEvent.sha256)
        }.onFailure { error ->
            emit(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = "Couldn't download the APK: ${error.message ?: error.javaClass.simpleName}",
                ),
            )
        }.getOrNull()
        if (downloaded != null) {
            emit(
                BuildEvent.ArtifactProduced(
                    downloaded.file,
                    downloaded.kind,
                    downloaded.file.length(),
                    wireEvent.sha256,
                    wireEvent.signed,
                    wireEvent.certificateSha256,
                ),
            )
        }
        return downloaded != null
    }

    private fun terminalFailureMessage(status: BuildStatusResponse): String {
        val raw = status.errorMessage ?: "Build ${status.status.lowercase()}"
        return if (raw.contains("OOM", ignoreCase = true) ||
            raw.contains("evict", ignoreCase = true) ||
            raw.contains("out of memory", ignoreCase = true)
        ) {
            "Build worker ran out of memory and was stopped. Tap Run to try again."
        } else {
            raw
        }
    }

    private fun incompleteMessage(timedOut: Boolean): String = if (timedOut) {
        "Build is still running on the server but this session timed out waiting for updates. " +
            "Re-open the project to reconnect, or tap Run to start a new build."
    } else {
        "Lost connection to the build server before the build finished. " +
            "The build may still be running on the server. Re-open the project to reconnect, " +
            "or tap Run to try again."
    }

    private sealed interface Frame {
        data class Text(val text: String) : Frame
        data object Closed : Frame
        data class Failure(val error: Throwable) : Frame
    }

    private data class FollowState(
        var finished: Boolean = false,
        var reconnectAttempt: Int = 0,
        var processedTextFrames: Int = 0,
    )

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
        const val BUILD_FOLLOW_TIMEOUT_MS = 5_700_000L
        const val STATUS_POLL_INTERVAL_MS = 5_000L
    }
}

internal data class BuildStreamRequest(
    val buildId: String,
    val projectRoot: File,
    val parser: BuildEventParser,
    val startedAt: Long,
)
