package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildEventParser
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ProjectModelMapper
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RemoteJson
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.WireProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.id.IdGenerator
import com.ahmadkharfan.androidstudiolite.domain.id.UuidIdGenerator
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import com.ahmadkharfan.androidstudiolite.domain.time.SystemMonotonicClock
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.launch
import okhttp3.WebSocket

class RemoteBuildSystem internal constructor(
    private val gateway: RemoteBuildGateway,
    private val packager: ProjectPackager,
    private val downloadArtifact: suspend (
        buildId: String,
        fallbackName: String?,
        expectedSizeBytes: Long?,
        expectedSha256: String?,
    ) -> ArtifactDownloader.DownloadedArtifact?,
    private val gradleReader: GradleProjectReader,
    private val sourceDir: File,
    private val preferGitSource: suspend () -> Boolean = { false },
    private val gitSourceResolver: suspend (File) -> GitRemoteInfo? = { null },
    private val releaseSigningResolver: suspend () -> SigningConfig? = { null },
    private val encodeBase64: (ByteArray) -> String = { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val ids: IdGenerator = UuidIdGenerator,
    private val cancelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BuildSystem {

    private val streamFollower = BuildStreamFollower(gateway, downloadArtifact, clock)

    constructor(
        client: RemoteClient,
        packager: ProjectPackager,
        artifactDownloader: ArtifactDownloader,
        gradleReader: GradleProjectReader,
        sourceDir: File,
        preferGitSource: suspend () -> Boolean = { false },
        gitSourceResolver: suspend (File) -> GitRemoteInfo? = { null },
        releaseSigningResolver: suspend () -> SigningConfig? = { null },
        encodeBase64: (ByteArray) -> String = {
            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
        },
        clock: MonotonicClock = SystemMonotonicClock,
        ids: IdGenerator = UuidIdGenerator,
        cancelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(
        gateway = client,
        packager = packager,
        downloadArtifact = artifactDownloader::download,
        gradleReader = gradleReader,
        sourceDir = sourceDir,
        preferGitSource = preferGitSource,
        gitSourceResolver = gitSourceResolver,
        releaseSigningResolver = releaseSigningResolver,
        encodeBase64 = encodeBase64,
        clock = clock,
        ids = ids,
        cancelScope = cancelScope,
    )

    @Volatile private var currentBuildId: String? = null

    override suspend fun sync(projectRoot: File): ProjectModel {
        return runCatching {
            var artifact: File? = null
            var failure: String? = null
            build(
                BuildRequest(
                    projectRoot = projectRoot,
                    modulePath = ":",
                    variantName = "model",
                    kind = BuildKind.MODEL,
                    operationId = "model-${ids.newId()}",
                    taskPath = "aslModel",
                ),
            ).collect { event ->
                when (event) {
                    is BuildEvent.ArtifactProduced -> artifact = event.file
                    is BuildEvent.Problem -> if (event.severity == BuildEvent.ProblemSeverity.ERROR) failure = event.message
                    is BuildEvent.Finished -> if (!event.success) error(failure ?: "Gradle model sync failed")
                    else -> Unit
                }
            }
            val modelFile = artifact ?: error("Gradle model sync produced no model")
            ProjectModelMapper.toDomain(
                RemoteJson.decodeFromString<WireProjectModel>(modelFile.readText()),
                projectRoot,
            )
        }.getOrElse { error ->
            android.util.Log.w(
                "RemoteBuildSystem",
                "Gradle model sync failed; using static project parse. Variant/flavor detection " +
                    "may be incomplete for this project.",
                error,
            )
            gradleReader.read(projectRoot).model
        }
    }

    override fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val projectRoot = request.projectRoot
        val parser = BuildEventParser()
        val startedAt = clock.elapsedMillis()
        var socket: WebSocket? = null
        try {
            send(BuildEvent.Started(request))


            val gitSource = if (RemoteBuildRequestFactory.shouldUseGit(preferGitSource(), request)) {
                gitSourceResolver(projectRoot)
            } else {
                null
            }
            val signing = resolveSigning(request)
            if (signing != null) gateway.requireSecureSigningTransport()


            val zip = if (gitSource == null) packager.packageProjectCached(projectRoot, sourceDir) else null


            val created = gateway.createBuild(
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
                gateway.uploadSource(uploadUrl, zip, created.uploadMethod ?: "PUT")
            }


            gateway.startBuild(created.buildId)


            streamFollower.follow(
                request = BuildStreamRequest(created.buildId, projectRoot, parser, startedAt),
                socketHolder = { socket = it },
                emit = { send(it) },
            )
        } catch (e: CancellationException) {

            throw e
        } catch (t: Throwable) {


            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = BuildErrorMessages.userFacingBuildError(t),
                ),
            )
            send(BuildEvent.Finished(success = false, durationMillis = clock.elapsedMillis() - startedAt))
        } finally {
            socket?.cancel()
            currentBuildId = null
        }
    }

    override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = channelFlow {
        val parser = BuildEventParser()
        val startedAt = clock.elapsedMillis()
        var socket: WebSocket? = null
        try {
            currentBuildId = buildId
            send(BuildEvent.RemoteBuildBound(buildId))


            val existing = runCatching { gateway.buildStatus(buildId) }.getOrNull()
            if (existing != null && streamFollower.isTerminalStatus(existing.status)) {
                send(BuildEvent.Progress("Build finished. Collecting results…"))
                streamFollower.emitTerminalStatus(buildId, existing, startedAt) { send(it) }
                return@channelFlow
            }
            send(BuildEvent.Progress(streamFollower.statusProgressLabel(existing?.status)))

            streamFollower.follow(
                request = BuildStreamRequest(buildId, projectRoot, parser, startedAt),
                socketHolder = { socket = it },
                emit = { send(it) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            send(
                BuildEvent.Problem(
                    severity = BuildEvent.ProblemSeverity.ERROR,
                    message = BuildErrorMessages.userFacingBuildError(t),
                ),
            )
            send(BuildEvent.Finished(success = false, durationMillis = clock.elapsedMillis() - startedAt))
        } finally {
            socket?.cancel()
            currentBuildId = null
        }
    }

    override fun cancel() {
        val id = currentBuildId ?: return
        cancelScope.launch { runCatching { gateway.cancelBuild(id) } }
    }

    private suspend fun resolveSigning(request: BuildRequest) =
        if (request.buildType.equals("release", ignoreCase = true) ||
            (request.buildType == null && RemoteBuildRequestFactory.isReleaseVariant(request.variantName))) {
            val config = releaseSigningResolver()
                ?: throw IllegalStateException("Configure a release keystore in Settings before building a release artifact.")
            RemoteBuildRequestFactory.releaseSigningMaterial(config, encodeBase64 = encodeBase64)
                ?: throw IllegalStateException("The configured release keystore is missing or unreadable.")
        } else {
            null
        }

}

private const val STREAM_RECONNECT_BACKOFF_MS = 1_000L
private const val STREAM_RECONNECT_BACKOFF_CAP_MS = 30_000L

internal fun reconnectBackoffMillis(attempt: Int): Long = min(
    STREAM_RECONNECT_BACKOFF_CAP_MS,
    STREAM_RECONNECT_BACKOFF_MS shl min(attempt, 5),
)

internal fun boundedWaitMillis(desiredMillis: Long, remainingMillis: Long): Long =
    min(desiredMillis, remainingMillis).coerceAtLeast(0)
