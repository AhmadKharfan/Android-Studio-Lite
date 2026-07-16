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
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlinx.coroutines.CancellationException
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
 * the existing build console. A `build()` selects the source — a Git remote (A3, no upload) when the
 * user opted in and the project has one, else zip-upload (A2's default) — starts the build, and relays
 * the control-plane WebSocket's [BuildEvent] stream, downloading the produced artifact so the unchanged
 * install/run flow finds a real APK on disk. Release builds attach the user's keystore material
 * (over TLS) via [resolveSigning]. `cancel()` posts to the cancel endpoint; `sync()` calls the server
 * sync (falling back to the on-device static parser until it ships).
 *
 * Bound as the single `BuildSystem` in the Koin graph.
 */
class RemoteBuildSystem(
    private val client: RemoteClient,
    private val packager: ProjectPackager,
    private val artifactDownloader: ArtifactDownloader,
    private val gradleReader: GradleProjectReader,
    /**
     * Cache dir for source zips (e.g. `context.cacheDir/build-sources`). Holds one archive, reused
     * across builds of an unchanged project; safe to purge (it is rebuilt on the next Run).
     */
    private val sourceDir: File,
    /** Whether the user opted to build from the project's Git remote when one exists (A3). */
    private val preferGitSource: suspend () -> Boolean = { false },
    /** Resolves the project's Git remote URL + current ref, or null for local-only projects. */
    private val gitSourceResolver: suspend (File) -> GitRemoteInfo? = { null },
    /** The user's remembered release keystore, or null when none is configured. */
    private val releaseSigningResolver: suspend () -> SigningConfig? = { null },
    /** Base64 encoder for the release keystore bytes (Android's by default; overridden in tests). */
    private val encodeBase64: (ByteArray) -> String = { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
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
            // 1. Resolve the source: Git remote (no upload) when the user opted in and the project has
            //    one, else zip-upload (A2's default, and always for local-only projects).
            val gitSource = if (RemoteBuildRequestFactory.shouldUseGit(preferGitSource(), request)) {
                gitSourceResolver(projectRoot)
            } else {
                null
            }
            val signing = resolveSigning(request)

            // 2. Package first, so the create can carry the source's hash. Reuses the previous
            //    archive when nothing in the project changed, which is the common Run-again case.
            //    The zip is owned by the packager's cache, so it deliberately outlives this build
            //    and is not deleted in `finally`.
            val zip = if (gitSource == null) packager.packageProjectCached(projectRoot, sourceDir) else null

            // 3. Create the build. The hash lets the server say "already have that exact tree";
            //    the project key names its configuration-cache slot server-side.
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

            // 4. Upload — unless the server already has this exact source. Zipping was already
            //    skipped for an unchanged project; this skips the transfer too, which is the part
            //    the user actually waits on over mobile data.
            if (zip != null && created.sourceUploadRequired) {
                val uploadUrl = created.uploadUrl
                    ?: throw RemoteException(0, null, "Server returned no upload URL for a zip build")
                client.uploadSource(uploadUrl, zip, created.uploadMethod ?: "PUT")
            }

            // 5. Start the build.
            client.startBuild(created.buildId)

            // 6. Stream BuildEvents over the WebSocket. Frames are drained off the OkHttp callback
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
        } catch (e: CancellationException) {
            // cancel() / collector scope death — not a failure, and must stay cooperative.
            throw e
        } catch (t: Throwable) {
            // Anything else (network down, 4xx/5xx, a wire-shape change) is a FAILED BUILD, not a
            // dead IDE. Previously this escaped the flow and crashed the app: a MissingFieldException
            // from decoding /start's `{"status":"queued"}` took the whole process down. Surface it in
            // the build console the same way a compile error arrives.
            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = t.message ?: t::class.simpleName ?: "Build failed",
                ),
            )
            send(BuildEvent.Finished(success = false, durationMillis = System.currentTimeMillis() - startedAt))
        } finally {
            socket?.cancel()
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

    /**
     * The release-signing payload for a release build, or null for debug builds / when no release
     * keystore is set up. The keystore file + passwords are collected here and transmitted (over TLS)
     * in the build request; the local copy stays in [com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager]'s
     * EncryptedSharedPreferences.
     */
    private suspend fun resolveSigning(request: BuildRequest) =
        if (RemoteBuildRequestFactory.isReleaseVariant(request.variantName)) {
            RemoteBuildRequestFactory.releaseSigningMaterial(
                config = releaseSigningResolver(),
                encodeBase64 = encodeBase64,
            )
        } else {
            null
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
