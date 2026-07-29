package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.Context
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuild
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.id.IdGenerator
import com.ahmadkharfan.androidstudiolite.domain.id.UuidIdGenerator
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.time.AslClock
import com.ahmadkharfan.androidstudiolite.domain.time.SystemAslClock
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.BuildPreflight
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.BuildPreflightResult
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.DeviceStorage
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.ToolchainVersions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
)

internal fun isActiveBuildFresh(nowMillis: Long, startedAtEpochMs: Long, maxAgeMs: Long): Boolean =
    nowMillis - startedAtEpochMs <= maxAgeMs

class BuildRunCoordinator internal constructor(
    private val context: Context,
    private val buildSystem: BuildSystem,
    private val keystoreManager: KeystoreManager,
    private val installOperations: BuildInstallOperations,
    private val gradleReader: GradleProjectInspector,
    private val notifier: BuildNotifier,
    private val activeBuildStore: ActiveBuildRepository,
    private val clock: AslClock,
    private val ids: IdGenerator,
) : BuildRunApi {

    private val installRunner = BuildInstallRunner(installOperations, gradleReader, ids)

    constructor(
        context: Context,
        buildSystem: BuildSystem,
        keystoreManager: KeystoreManager,
        apkInstaller: ApkInstaller,
        gradleReader: GradleProjectInspector,
        notifier: BuildNotifier,
        activeBuildStore: ActiveBuildRepository,
        clock: AslClock = SystemAslClock,
        ids: IdGenerator = UuidIdGenerator,
    ) : this(
        context = context,
        buildSystem = buildSystem,
        keystoreManager = keystoreManager,
        installOperations = BuildInstallOperations(
            install = apkInstaller::install,
            uninstall = apkInstaller::uninstall,
            cancelActiveInstall = apkInstaller::cancelActiveInstall,
        ),
        gradleReader = gradleReader,
        notifier = notifier,
        activeBuildStore = activeBuildStore,
        clock = clock,
        ids = ids,
    )

    private val clearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val admissionMutex = Mutex()
    private val _execution = MutableStateFlow(BuildExecutionSnapshot())
    override val execution: StateFlow<BuildExecutionSnapshot> = _execution.asStateFlow()
    @Volatile private var admittedOperationId: String? = null
    @Volatile private var activeExecutionJob: Job? = null
    @Volatile private var cancelledOperationId: String? = null

    override suspend fun start(request: BuildRequest, meta: BuildClientMeta): StartBuildResult =
        admissionMutex.withLock {
            releaseSigningFailure(request)?.let { return@withLock it }
            val current = admittedOperationId
            if (current != null) {
                return@withLock StartBuildResult.AlreadyRunning(current, _execution.value.projectId)
            }
            resumePersistedBuild()?.let { return@withLock it }
            val operationId = ids.newId()
            cancelledOperationId = null
            admittedOperationId = operationId
            _execution.value = preparingExecution(operationId, request, meta)
            startAdmittedBuild(newActiveBuild(operationId, request, meta), request, meta)
        }

    private suspend fun releaseSigningFailure(request: BuildRequest): StartBuildResult.Failed? {
        val release = request.buildType.equals("release", ignoreCase = true) ||
            (request.buildType == null && RunTargetResolver.isReleaseVariant(request.variantName))
        if (!release) return null
        val releaseConfig = runCatching { keystoreManager.releaseSigningConfig() }
        if (releaseConfig.isSuccess && releaseConfig.getOrNull() != null) return null
        return StartBuildResult.Failed(
            releaseConfig.exceptionOrNull()?.message
                ?: "Configure a valid release keystore in Settings before building ${request.variantName}.",
        )
    }

    private suspend fun resumePersistedBuild(): StartBuildResult? {
        val persisted = activeBuildStore.get() ?: return null
        if (!isActiveBuildFresh(clock.nowMillis(), persisted.startedAtEpochMs, ACTIVE_BUILD_MAX_AGE_MS)) {
            activeBuildStore.clear(persisted.buildId)
            return null
        }
        return runCatching { adoptPersisted(persisted) }.fold(
            onSuccess = { StartBuildResult.AlreadyRunning(persisted.operationId, persisted.projectId) },
            onFailure = { error ->
                admittedOperationId = null
                _execution.value = _execution.value.copy(active = false)
                StartBuildResult.Failed(error.message ?: "Could not reconnect the background build service")
            },
        )
    }

    private fun preparingExecution(
        operationId: String,
        request: BuildRequest,
        meta: BuildClientMeta,
    ) = BuildExecutionSnapshot(
        operationId = operationId,
        projectId = meta.projectId,
        projectName = meta.projectName,
        console = BuildConsoleState(status = BuildStatus.Running, request = request, progressMessage = "Preparing…"),
        installRequested = meta.installAfterSuccess,
        active = true,
        phase = BuildExecutionPhase.Preparing,
    )

    private fun newActiveBuild(
        operationId: String,
        request: BuildRequest,
        meta: BuildClientMeta,
    ) = ActiveBuild(
        buildId = "",
        operationId = operationId,
        projectId = meta.projectId,
        projectRootPath = request.projectRoot.absolutePath,
        projectName = meta.projectName,
        installAfterSuccess = meta.installAfterSuccess,
        autoLaunchAfterInstall = meta.autoLaunchAfterInstall,
        startedAtEpochMs = clock.nowMillis(),
        modulePath = request.modulePath,
        variantName = request.variantName,
        kind = request.kind.name,
        taskPath = request.taskPath,
        buildType = request.buildType,
    )

    private suspend fun startAdmittedBuild(
        active: ActiveBuild,
        request: BuildRequest,
        meta: BuildClientMeta,
    ): StartBuildResult {
        return try {
            activeBuildStore.save(active)
            RemoteBuildKeepAliveService.startExecution(
                context,
                active.operationId,
                request.copy(operationId = active.operationId),
                meta,
            )
            StartBuildResult.Accepted(active.operationId)
        } catch (error: Exception) {
            rollbackFailedAdmission(active, error)
            StartBuildResult.Failed(error.message ?: "Could not start the background build service")
        }
    }

    private suspend fun rollbackFailedAdmission(active: ActiveBuild, error: Exception) {
        admittedOperationId = null
        _execution.value = _execution.value.copy(
            console = _execution.value.console.copy(
                status = BuildStatus.Failed,
                progressMessage = null,
                problems = listOf(
                    BuildProblem(
                        BuildEvent.ProblemSeverity.ERROR,
                        error.message ?: "Android could not start the background build service",
                    ),
                ),
            ),
            active = false,
        )
        activeBuildStore.clear(active.buildId)
    }

    private fun adoptPersisted(active: ActiveBuild) {
        val request = BuildRequest(
            File(active.projectRootPath),
            active.modulePath,
            active.variantName,
            runCatching { BuildKind.valueOf(active.kind) }
                .getOrDefault(BuildKind.ASSEMBLE),
            active.operationId,
            active.taskPath,
            active.buildType,
        )
        val meta = BuildClientMeta(
            active.projectId,
            active.projectName,
            active.installAfterSuccess,
            active.autoLaunchAfterInstall,
        )
        val previousOperationId = admittedOperationId
        val previousExecution = _execution.value
        admittedOperationId = active.operationId
        _execution.value = BuildExecutionSnapshot(
            operationId = active.operationId,
            projectId = active.projectId,
            projectName = active.projectName,
            console = BuildConsoleState(status = BuildStatus.Running, request = request, progressMessage = "Reconnecting…"),
            installRequested = active.installAfterSuccess,
            active = true,
            phase = BuildExecutionPhase.Reconnecting,
        )
        try {
            RemoteBuildKeepAliveService.startExecution(
                context,
                active.operationId,
                request,
                meta,
                attachBuildId = active.buildId.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            admittedOperationId = previousOperationId
            _execution.value = previousExecution
            throw t
        }
    }

    override suspend fun recover(projectId: String): Boolean = admissionMutex.withLock {
        if (admittedOperationId != null) return@withLock true
        val active = activeBuildStore.get() ?: return@withLock false
        if (active.projectId != projectId) return@withLock false
        if (!isActiveBuildFresh(clock.nowMillis(), active.startedAtEpochMs, ACTIVE_BUILD_MAX_AGE_MS)) {
            activeBuildStore.clear(active.buildId)
            return@withLock false
        }
        runCatching { adoptPersisted(active) }.isSuccess
    }

    internal suspend fun execute(
        operationId: String,
        request: BuildRequest,
        meta: BuildClientMeta,
        attachBuildId: String? = null,
    ) {
        activeExecutionJob = currentCoroutineContext()[Job]
        val persisted = admitExecution(operationId, request, meta) ?: return
        if (admittedOperationId != operationId) return
        try {
            val events = buildEvents(request, persisted, attachBuildId)
            val console = collectBuildEvents(events, meta, request.projectRoot, operationId)
            finishExecution(console, request, meta)
        } finally {
            releaseExecution(operationId)
        }
    }

    private suspend fun admitExecution(
        operationId: String,
        request: BuildRequest,
        meta: BuildClientMeta,
    ): ActiveBuild? = admissionMutex.withLock {
        val active = activeBuildStore.get()?.takeIf { it.operationId == operationId }
            ?: return@withLock null
        if (admittedOperationId == null) {
            admittedOperationId = operationId
            _execution.value = BuildExecutionSnapshot(
                operationId = operationId,
                projectId = meta.projectId,
                projectName = meta.projectName,
                console = BuildConsoleState(
                    status = BuildStatus.Running,
                    request = request,
                    progressMessage = "Reconnecting…",
                ),
                installRequested = meta.installAfterSuccess,
                active = true,
                phase = BuildExecutionPhase.Reconnecting,
            )
        }
        active
    }

    private fun buildEvents(
        request: BuildRequest,
        persisted: ActiveBuild,
        attachBuildId: String?,
    ): Flow<BuildEvent> {
        val effectiveAttachId = attachBuildId?.takeIf { it.isNotBlank() }
            ?: persisted.buildId.takeIf { it.isNotBlank() }
        return if (effectiveAttachId == null) {
            buildSystem.build(request)
        } else {
            buildSystem.attach(effectiveAttachId, request.projectRoot)
        }
    }

    private suspend fun collectBuildEvents(
        events: Flow<BuildEvent>,
        meta: BuildClientMeta,
        projectRoot: File,
        operationId: String,
    ): BuildConsoleState {
        var console = _execution.value.console
        events.onEach { event -> onBuildEvent(event, meta, projectRoot, operationId) }.collect { event ->
            if (cancelledOperationId == operationId) {
                throw CancellationException("Build cancelled")
            }
            console = console.reduce(event)
            _execution.value = _execution.value.copy(
                console = console,
                phase = phaseFor(event, _execution.value.phase),
            )
        }
        return console
    }

    private suspend fun finishExecution(
        collectedConsole: BuildConsoleState,
        request: BuildRequest,
        meta: BuildClientMeta,
    ) {
        var console = collectedConsole
        if (console.status == BuildStatus.Running) {
            console = console.copy(status = BuildStatus.Failed, progressMessage = null)
            _execution.value = _execution.value.copy(
                console = console,
                phase = BuildExecutionPhase.Failed,
            )
        }
        if (console.status == BuildStatus.Succeeded && meta.installAfterSuccess) {
            installFromService(console, request, meta)
        } else {
            notifier.notifyFinished(
                meta.projectName,
                console.status == BuildStatus.Succeeded,
                console.durationMillis,
                meta.projectId,
                false,
            )
        }
    }

    private suspend fun releaseExecution(operationId: String) {
        if (activeExecutionJob == currentCoroutineContext()[Job]) activeExecutionJob = null
        admissionMutex.withLock {
            if (_execution.value.operationId == operationId) {
                _execution.value = _execution.value.copy(active = false)
            }
            activeBuildStore.get()
                ?.takeIf { it.operationId == operationId }
                ?.let { activeBuildStore.clear(it.buildId) }
            if (admittedOperationId == operationId) admittedOperationId = null
        }
        RemoteBuildKeepAliveService.stopBuilding(context)
    }

    private suspend fun installFromService(
        console: BuildConsoleState,
        request: BuildRequest,
        meta: BuildClientMeta,
    ) {
        val started = installRunner.install(
            request = BuildInstallRequest(
                console = console,
                buildRequest = request,
                autoLaunch = meta.autoLaunchAfterInstall,
                operationId = _execution.value.operationId,
            ),
            onInstalling = {
                _execution.value = _execution.value.copy(
                    installState = InstallExecutionState.Preparing,
                    phase = BuildExecutionPhase.Installing,
                )
            },
            onEvent = { event -> _execution.value = _execution.value.reduceInstallEvent(event) },
        )
        if (!started) {
            _execution.value = _execution.value.copy(
                installState = InstallExecutionState.Failed,
                phase = BuildExecutionPhase.Failed,
            )
            notifier.notifyFinished(meta.projectName, false, console.durationMillis, meta.projectId, false)
        }
    }

    internal fun onInstallTerminal(operationId: String, success: Boolean) {
        if (_execution.value.operationId != operationId) return
        _execution.value = _execution.value.copy(
            installState = if (success) InstallExecutionState.Installed else InstallExecutionState.Failed,
            active = false,
            phase = if (success) BuildExecutionPhase.Succeeded else BuildExecutionPhase.Failed,
        )
    }

    override fun uninstallConflict(packageName: String) {
        if (packageName.isBlank()) return
        clearScope.launch {
            installOperations.uninstall(packageName).collect { event ->
                _execution.value = when (event) {
                    UninstallEvent.Uninstalling -> _execution.value.copy(
                        installState = InstallExecutionState.Preparing,
                        installFailureReason = null,
                    )
                    UninstallEvent.AwaitingConfirmation -> _execution.value.copy(
                        installState = InstallExecutionState.AwaitingConfirmation,
                    )
                    UninstallEvent.Uninstalled -> _execution.value.copy(
                        installState = InstallExecutionState.None,
                        installConflictPackage = null,
                    )
                    is UninstallEvent.Failed -> _execution.value.copy(
                        installState = InstallExecutionState.Failed,
                        installFailureReason = event.reason,
                    )
                }
            }
        }
    }

    override suspend fun preflight(projectRoot: File): BuildPreflightResult = withContext(Dispatchers.IO) {
        val versions = runCatching {
            val read = gradleReader.inspect(projectRoot)
            ToolchainVersions(
                gradle = read.gradleVersion,
                agp = read.agpVersion,
                jdkMajor = TOOLCHAIN_JDK_MAJOR,
            )
        }.getOrDefault(ToolchainVersions(jdkMajor = TOOLCHAIN_JDK_MAJOR))
        BuildPreflight.run(versions, DeviceStorage.availableBytes(projectRoot))
    }

    override suspend fun syncProject(projectRoot: File) = buildSystem.sync(projectRoot)

    override suspend fun ensureDebugKeystore() {
        runCatching { keystoreManager.debugSigningConfig() }
    }

    override fun cancel() {
        val operationId = _execution.value.operationId
        if (operationId == null || !_execution.value.active) return
        cancelledOperationId = operationId
        _execution.value = _execution.value.copy(phase = BuildExecutionPhase.Cancelling)
        buildSystem.cancel()
        installOperations.cancelActiveInstall()
        activeExecutionJob?.cancel(CancellationException("Cancelled by user"))
        _execution.value = _execution.value.copy(
            console = _execution.value.console.copy(status = BuildStatus.Cancelled, progressMessage = null),
            active = false,
            phase = BuildExecutionPhase.Cancelled,
        )
        demoteKeepAlive()
        clearScope.launch {
            admissionMutex.withLock {
                activeBuildStore.get()
                    ?.takeIf { it.operationId == operationId }
                    ?.let { activeBuildStore.clear(it.buildId) }
                if (admittedOperationId == operationId) admittedOperationId = null
            }
        }
    }

    override fun canPostNotifications(): Boolean = notifier.canPost()

    private suspend fun onBuildEvent(
        event: BuildEvent,
        meta: BuildClientMeta,
        projectRoot: File,
        operationId: String? = admittedOperationId,
    ) {
        when (event) {
            is BuildEvent.RemoteBuildBound -> {
                val existing = activeBuildStore.get()
                if (operationId != null && existing?.operationId != operationId) return
                activeBuildStore.save(
                    ActiveBuild(
                        buildId = event.buildId,
                        operationId = operationId.orEmpty(),
                        projectId = meta.projectId,
                        projectRootPath = projectRoot.absolutePath,
                        projectName = meta.projectName,
                        installAfterSuccess = meta.installAfterSuccess,
                        autoLaunchAfterInstall = meta.autoLaunchAfterInstall,
                        startedAtEpochMs = existing
                            ?.takeIf { it.buildId == event.buildId }
                            ?.startedAtEpochMs
                            ?: clock.nowMillis(),
                        modulePath = existing?.modulePath.orEmpty(),
                        variantName = existing?.variantName.orEmpty(),
                        kind = existing?.kind ?: "ASSEMBLE",
                        taskPath = existing?.taskPath,
                        buildType = existing?.buildType,
                    ),
                )
            }
            is BuildEvent.Progress -> {
                RemoteBuildKeepAliveService.updateProgress(
                    context,
                    meta.projectId,
                    meta.projectName,
                    event.message,
                )
            }
            is BuildEvent.Finished -> Unit
            else -> Unit
        }
    }

    private fun phaseFor(event: BuildEvent, current: BuildExecutionPhase): BuildExecutionPhase = when (event) {
        is BuildEvent.Started, is BuildEvent.RemoteBuildBound, is BuildEvent.TaskStarted,
        is BuildEvent.TaskFinished, is BuildEvent.Output -> BuildExecutionPhase.Running
        is BuildEvent.Progress -> when {
            event.message.contains("download", ignoreCase = true) -> BuildExecutionPhase.DownloadingArtifact
            event.message.contains("reconnect", ignoreCase = true) ||
                event.message.contains("retry", ignoreCase = true) -> BuildExecutionPhase.Reconnecting
            else -> BuildExecutionPhase.Running
        }
        is BuildEvent.ArtifactProduced -> BuildExecutionPhase.DownloadingArtifact
        is BuildEvent.Problem -> if (event.message.contains("timed out", ignoreCase = true)) {
            BuildExecutionPhase.TimedOut
        } else {
            current
        }
        is BuildEvent.Finished -> when {
            current == BuildExecutionPhase.TimedOut -> current
            event.success -> BuildExecutionPhase.Succeeded
            else -> BuildExecutionPhase.Failed
        }
    }

    private fun demoteKeepAlive() {
        RemoteBuildKeepAliveService.stopBuilding(context)
    }

    private companion object {
        const val TOOLCHAIN_JDK_MAJOR = 17
        const val ACTIVE_BUILD_MAX_AGE_MS = 5_760_000L // 96 min
    }
}
