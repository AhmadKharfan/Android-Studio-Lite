package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SourceSnapshotPusher
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildTasks
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.id.IdGenerator
import com.ahmadkharfan.androidstudiolite.domain.id.UuidIdGenerator
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import com.ahmadkharfan.androidstudiolite.domain.time.SystemMonotonicClock
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where builds run, on which JDK, and where their results are downloaded to. */
data class GitHubActionsConfig(
    val downloadDir: File,
    val repositoryName: String = "asl-build",
    val javaVersion: Int = 17,
    /** Where build repositories are pushed to; the repository's full name and `.git` are appended. */
    val gitBaseUrl: String = "https://github.com/",
)

/** Collaborators of [GitHubActionsBuildSystem] that tests replace. */
internal class GitHubActionsSeams(
    val clock: MonotonicClock = SystemMonotonicClock,
    val ids: IdGenerator = UuidIdGenerator,
    val wait: suspend (Long) -> Unit = { delay(it.milliseconds) },
    val cancelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
)

/**
 * Builds projects with GitHub Actions in the signed-in user's private build repository.
 *
 * A build pushes a snapshot of the project, dispatches the managed workflow on it, follows the run,
 * and downloads the verified result. Build ids are [BuildHandle]s, so a build can be re-attached after
 * the app was killed, even if it died before GitHub reported the run id.
 */
class GitHubActionsBuildSystem internal constructor(
    private val api: GitHubApiClient,
    private val token: suspend () -> String?,
    private val snapshots: SourceSnapshotPusher,
    private val inspector: GradleProjectInspector,
    private val config: GitHubActionsConfig,
    signing: ApkSigning?,
    private val seams: GitHubActionsSeams,
) : BuildSystem {

    /** @param signing re-signs APKs with this device's keys; without it APKs keep the runner's signature. */
    constructor(
        api: GitHubApiClient,
        token: suspend () -> String?,
        snapshots: SourceSnapshotPusher,
        inspector: GradleProjectInspector,
        config: GitHubActionsConfig,
        signing: ApkSigning? = null,
    ) : this(api, token, snapshots, inspector, config, signing, GitHubActionsSeams())

    private val provisioner = BuildRepositoryProvisioner(api, config.repositoryName, config.gitBaseUrl, seams.wait)
    private val collector = ResultCollector(api, config.downloadDir, signing)
    private val follower = RunFollower(api, seams.clock, seams.wait)

    @Volatile private var active: ActiveRun? = null

    override suspend fun readiness(): BuildReadiness {
        if (token().isNullOrBlank()) return BuildReadiness.NeedsSignIn(GitHubBuildMessages.SIGN_IN_REQUIRED)
        val user = runCatching { api.authenticatedUser() }
        val error = user.exceptionOrNull()
        return when {
            error is GitHubApiException && error.isUnauthorized ->
                BuildReadiness.NeedsSignIn(GitHubBuildMessages.forError(error))
            user.getOrNull()?.scopes?.containsAll(REQUIRED_SCOPES) == false ->
                BuildReadiness.NeedsSignIn(GitHubBuildMessages.WORKFLOW_SCOPE_MISSING)
            else -> BuildReadiness.Ready
        }
    }

    override suspend fun sync(projectRoot: File): ProjectModel =
        withContext(Dispatchers.IO) { inspector.inspect(projectRoot).model }

    override fun build(request: BuildRequest): Flow<BuildEvent> = channelFlow {
        val startedAt = seams.clock.elapsedMillis()
        val run = ActiveRun().also { active = it }
        reportingFailures(startedAt, run, { send(it) }) {
            send(BuildEvent.Started(request))
            send(BuildEvent.StatusChanged(RemoteBuildPhase.PREPARING, "Preparing the GitHub build…"))
            val tasks = BuildTasks.forRequest(request)
            val repository = provisioner.ensure()
            send(BuildEvent.StatusChanged(RemoteBuildPhase.UPLOADING, "Uploading the project to GitHub…"))
            val snapshot = snapshots.push(
                projectRoot = request.projectRoot,
                remoteUrl = repository.cloneUrl,
                token = token() ?: throw BuildFailure(GitHubBuildMessages.SIGN_IN_REQUIRED),
                projectName = request.projectRoot.name,
            )
            val correlationId = seams.ids.newId()
            val inputs = BuildWorkflow.dispatchInputs(correlationId, snapshot.branch, snapshot.sha, tasks, config.javaVersion)
            run.handle = BuildHandle(repository.owner, repository.name, correlationId)
            val runId = dispatch(repository, inputs)
            followRun(run, repository, runId, request, startedAt) { send(it) }
        }
    }

    override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = channelFlow {
        val startedAt = seams.clock.elapsedMillis()
        val run = ActiveRun().also { active = it }
        reportingFailures(startedAt, run, { send(it) }) {
            val handle = BuildHandle.decode(buildId) ?: throw BuildFailure("This build can't be found on GitHub anymore.")
            run.handle = handle
            send(BuildEvent.StatusChanged(RemoteBuildPhase.RUNNING, "Reconnecting to the GitHub build…"))
            val repository = provisioner.ensure()
            followRun(run, repository, handle.runId, request = null, startedAt) { send(it) }
        }
    }

    override fun cancel() {
        val run = active ?: return
        run.cancelRequested = true
        val handle = run.handle ?: return
        seams.cancelScope.launch { cancelRemote(handle) }
    }

    private suspend fun followRun(
        run: ActiveRun,
        repository: BuildRepository,
        knownRunId: Long?,
        request: BuildRequest?,
        startedAt: Long,
        emit: suspend (BuildEvent) -> Unit,
    ) {
        val pending = checkNotNull(run.handle)
        emit(BuildEvent.RemoteBuildBound(pending.encode()))
        val runId = knownRunId ?: follower.resolveRunId(pending, repository)
        val handle = pending.copy(runId = runId).also { run.handle = it }
        emit(BuildEvent.RemoteBuildBound(handle.encode()))
        if (run.cancelRequested) seams.cancelScope.launch { cancelRemote(handle) }
        val finished = follower.follow(handle, runId, startedAt + FOLLOW_TIMEOUT_MS, emit)
        val success = collector.collect(handle, finished, request, emit)
        emit(BuildEvent.Finished(success, seams.clock.elapsedMillis() - startedAt))
    }

    /**
     * Dispatches the workflow. Right after the workflow file is first written, GitHub can answer 404 or
     * 422 for a short while until it registers the workflow; nothing was started then, so retrying is safe.
     */
    private suspend fun dispatch(repository: BuildRepository, inputs: Map<String, String>): Long? {
        var attempt = 0
        while (true) {
            val result = runCatching {
                api.dispatchWorkflow(repository.owner, repository.name, BuildWorkflow.FILE_NAME, repository.defaultBranch, inputs)
            }
            val error = result.exceptionOrNull() ?: return result.getOrNull()
            val notRegisteredYet = error is GitHubApiException && (error.isNotFound || error.isValidationFailure)
            if (!notRegisteredYet || attempt >= DISPATCH_ATTEMPTS - 1) throw error
            seams.wait(DISPATCH_RETRY_MS)
            attempt++
        }
    }

    /** Runs [block], turning any failure other than cancellation into a problem and a failed finish. */
    private suspend fun reportingFailures(
        startedAt: Long,
        run: ActiveRun,
        emit: suspend (BuildEvent) -> Unit,
        block: suspend () -> Unit,
    ) {
        val failure = runCatching { block() }.exceptionOrNull()
        if (active === run) active = null
        when (failure) {
            null -> Unit
            is CancellationException -> {
                // The coordinator cancels collection right after cancel(); make sure the run stops too.
                run.handle?.takeIf { run.cancelRequested }?.let { seams.cancelScope.launch { cancelRemote(it) } }
                throw failure
            }
            else -> {
                emit(BuildEvent.Problem(BuildEvent.ProblemSeverity.ERROR, GitHubBuildMessages.forError(failure)))
                emit(BuildEvent.Finished(success = false, durationMillis = seams.clock.elapsedMillis() - startedAt))
            }
        }
    }

    private suspend fun cancelRemote(handle: BuildHandle) {
        runCatching {
            val runId = handle.runId ?: follower.resolveRunId(handle, provisioner.ensure())
            api.cancelRun(handle.owner, handle.repo, runId)
        }
    }

    private class ActiveRun {
        @Volatile var handle: BuildHandle? = null
        @Volatile var cancelRequested: Boolean = false
    }

    companion object {
        private const val FOLLOW_TIMEOUT_MS = 75 * 60 * 1000L
        private const val DISPATCH_ATTEMPTS = 6
        private const val DISPATCH_RETRY_MS = 5_000L
        private val REQUIRED_SCOPES = setOf("repo", "workflow")
    }
}
