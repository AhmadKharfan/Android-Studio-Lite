package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.Context
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuild
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.BuildPreflight
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.BuildPreflightResult
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.DeviceStorage
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.ToolchainVersions
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
)

class BuildRunCoordinator(
    private val context: Context,
    private val buildSystem: BuildSystem,
    private val keystoreManager: KeystoreManager,
    private val apkInstaller: ApkInstaller,
    private val gradleReader: GradleProjectReader,
    private val notifier: BuildNotifier,
    private val activeBuildStore: ActiveBuildRepository,
) : BuildRunApi {

    private val clearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val admissionMutex = Mutex()
    private val _execution = MutableStateFlow(BuildExecutionSnapshot())
    override val execution: StateFlow<BuildExecutionSnapshot> = _execution.asStateFlow()
    @Volatile private var admittedOperationId: String? = null

    override suspend fun start(request: BuildRequest, meta: BuildClientMeta): StartBuildResult =
        admissionMutex.withLock {
            val current = admittedOperationId
            if (current != null) {
                return@withLock StartBuildResult.AlreadyRunning(current, _execution.value.projectId)
            }
            activeBuildStore.get()?.let { persisted ->
                val fresh = System.currentTimeMillis() - persisted.startedAtEpochMs <= ACTIVE_BUILD_MAX_AGE_MS
                if (fresh) {
                    runCatching { adoptPersisted(persisted) }.getOrElse { error ->
                        admittedOperationId = null
                        _execution.value = _execution.value.copy(active = false)
                        return@withLock StartBuildResult.Failed(
                            error.message ?: "Could not reconnect the background build service",
                        )
                    }
                    return@withLock StartBuildResult.AlreadyRunning(
                        persisted.operationId,
                        persisted.projectId,
                    )
                }
                activeBuildStore.clear(persisted.buildId)
            }
            val operationId = UUID.randomUUID().toString()
            admittedOperationId = operationId
            _execution.value = BuildExecutionSnapshot(
                operationId = operationId,
                projectId = meta.projectId,
                projectName = meta.projectName,
                console = BuildConsoleState(status = BuildStatus.Running, request = request, progressMessage = "Preparing…"),
                installRequested = meta.installAfterSuccess,
                active = true,
                phase = BuildExecutionPhase.Preparing,
            )
            val active = ActiveBuild(
                buildId = "",
                operationId = operationId,
                projectId = meta.projectId,
                projectRootPath = request.projectRoot.absolutePath,
                projectName = meta.projectName,
                installAfterSuccess = meta.installAfterSuccess,
                autoLaunchAfterInstall = meta.autoLaunchAfterInstall,
                startedAtEpochMs = System.currentTimeMillis(),
                modulePath = request.modulePath,
                variantName = request.variantName,
                kind = request.kind.name,
            )
            try {
                activeBuildStore.save(active)
                RemoteBuildKeepAliveService.startExecution(
                    context,
                    operationId,
                    request.copy(operationId = operationId),
                    meta,
                )
            } catch (error: Exception) {
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
                return@withLock StartBuildResult.Failed(
                    error.message ?: "Could not start the background build service",
                )
            }
            StartBuildResult.Accepted(operationId)
        }

    private fun adoptPersisted(active: ActiveBuild) {
        val request = BuildRequest(
            File(active.projectRootPath),
            active.modulePath,
            active.variantName,
            runCatching { com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind.valueOf(active.kind) }
                .getOrDefault(com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind.ASSEMBLE),
            active.operationId,
        )
        val meta = BuildClientMeta(
            active.projectId,
            active.projectName,
            active.installAfterSuccess,
            active.autoLaunchAfterInstall,
        )
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
        RemoteBuildKeepAliveService.startExecution(
            context,
            active.operationId,
            request,
            meta,
            attachBuildId = active.buildId.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun recover(projectId: String): Boolean = admissionMutex.withLock {
        if (admittedOperationId != null) return@withLock true
        val active = activeBuildStore.get() ?: return@withLock false
        if (active.projectId != projectId) return@withLock false
        if (System.currentTimeMillis() - active.startedAtEpochMs > ACTIVE_BUILD_MAX_AGE_MS) {
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
        val persisted = admissionMutex.withLock {
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
        } ?: return
        if (admittedOperationId != operationId) return
        var console = _execution.value.console
        try {
            val effectiveAttachId = attachBuildId?.takeIf { it.isNotBlank() }
                ?: persisted.buildId.takeIf { it.isNotBlank() }
            val events = if (effectiveAttachId == null) {
                buildSystem.build(request)
            } else {
                buildSystem.attach(effectiveAttachId, request.projectRoot)
            }
            events.onEach { event ->
                onBuildEvent(event, meta, request.projectRoot, operationId)
            }.collect { event ->
                console = console.reduce(event)
                _execution.value = _execution.value.copy(
                    console = console,
                    phase = phaseFor(event, _execution.value.phase),
                )
            }
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
                notifier.notifyFinished(meta.projectName, console.status == BuildStatus.Succeeded,
                    console.durationMillis, meta.projectId, false)
            }
        } finally {
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
    }

    private suspend fun installFromService(
        console: BuildConsoleState,
        request: BuildRequest,
        meta: BuildClientMeta,
    ) {
        val artifact = console.artifact
        val apk = artifact?.takeIf { it.kind == BuildEvent.ArtifactKind.APK }?.let { File(it.path) }
        if (apk == null || !apk.isFile) {
            _execution.value = _execution.value.copy(
                installState = InstallExecutionState.Failed,
                phase = BuildExecutionPhase.Failed,
            )
            notifier.notifyFinished(meta.projectName, false, console.durationMillis, meta.projectId, false)
            return
        }
        val applicationId = resolveApplicationId(request.projectRoot, request.modulePath)
        _execution.value = _execution.value.copy(
            installState = InstallExecutionState.Preparing,
            phase = BuildExecutionPhase.Installing,
        )
        withTimeoutOrNull(INSTALL_OBSERVER_TIMEOUT_MS) {
            apkInstaller.install(
                apk,
                applicationId,
                meta.autoLaunchAfterInstall,
                requestToken = _execution.value.operationId ?: UUID.randomUUID().toString(),
            ).collect { event ->
                _execution.value = when (event) {
                    is InstallEvent.Preparing -> _execution.value.copy(
                        installState = InstallExecutionState.Preparing,
                        phase = BuildExecutionPhase.Installing,
                    )
                    is InstallEvent.AwaitingConfirmation -> _execution.value.copy(
                        installState = InstallExecutionState.AwaitingConfirmation,
                        phase = BuildExecutionPhase.AwaitingInstallConfirmation,
                    )
                    is InstallEvent.Installed -> _execution.value.copy(
                        installState = InstallExecutionState.Installed,
                        phase = BuildExecutionPhase.Succeeded,
                    )
                    is InstallEvent.Conflict, is InstallEvent.Failed ->
                        _execution.value.copy(
                            installState = InstallExecutionState.Failed,
                            phase = BuildExecutionPhase.Failed,
                        )
                }
            }
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

    override suspend fun preflight(projectRoot: File): BuildPreflightResult = withContext(Dispatchers.IO) {
        val versions = runCatching {
            val read = gradleReader.read(projectRoot)
            ToolchainVersions(
                gradle = read.gradleVersion,
                agp = read.catalog?.let { c -> AGP_VERSION_KEYS.firstNotNullOfOrNull { c.versions[it] } },
                jdkMajor = TOOLCHAIN_JDK_MAJOR,
            )
        }.getOrDefault(ToolchainVersions(jdkMajor = TOOLCHAIN_JDK_MAJOR))
        BuildPreflight.run(versions, DeviceStorage.availableBytes(projectRoot))
    }

    override suspend fun ensureDebugKeystore() {
        runCatching { keystoreManager.debugSigningConfig() }
    }

    override fun cancel() {
        val operationId = _execution.value.operationId
        buildSystem.cancel()
        apkInstaller.cancelActiveInstall()
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

    private suspend fun resolveApplicationId(projectRoot: File, modulePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val modules = gradleReader.read(projectRoot).model.modules
                val module = modules.firstOrNull { it.path == modulePath }
                    ?: modules.firstOrNull { it.type == ModuleType.ANDROID_APP }
                module?.applicationId
            }.getOrNull()
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
                            ?: System.currentTimeMillis(),
                        modulePath = existing?.modulePath ?: ":app",
                        variantName = existing?.variantName ?: "debug",
                        kind = existing?.kind ?: "ASSEMBLE",
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
        val AGP_VERSION_KEYS = listOf("agp", "androidGradlePlugin", "android-gradle-plugin", "android")
        const val ACTIVE_BUILD_MAX_AGE_MS = 2_100_000L
        const val INSTALL_OBSERVER_TIMEOUT_MS = 600_000L
    }
}
