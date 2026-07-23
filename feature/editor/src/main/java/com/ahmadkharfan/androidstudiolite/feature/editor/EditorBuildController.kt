package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildClientMeta
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RunTargetResolver
import com.ahmadkharfan.androidstudiolite.feature.buildrun.StartBuildResult
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightSeverity
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.PreflightWarning
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class EditorBuildController(
    private val projectId: String,
    private val scope: CoroutineScope,
    private val buildRunCoordinator: BuildRunApi,
    private val gradleProjectReader: GradleProjectReader,
    private val networkMonitor: NetworkMonitor?,
    private val projectRootPath: () -> String?,
    private val state: () -> EditorUiState,
    private val updateState: (EditorUiState.() -> EditorUiState) -> Unit,
    private val emitEffect: (EditorEffect) -> Unit,
    private val shouldLaunchAfterInstall: () -> Boolean,
    private val cancelAutoSave: () -> Unit,
    private val flushDirtyFiles: suspend () -> Boolean,
) {
    private var isBuildInFlight = false
    private var buildJob: Job? = null
    private var cachedProjectModel: Pair<String, ProjectModel>? = null

    fun cacheProjectModel(entry: Pair<String, ProjectModel>?) {
        cachedProjectModel = entry
    }

    fun run() = startBuild(variant = state().selectedVariant, kind = BuildKind.ASSEMBLE, install = true)

    fun buildRelease() {
        val kind = if (state().buildOutputAab) BuildKind.BUNDLE else BuildKind.ASSEMBLE
        startBuild(variant = "release", kind = kind, install = false)
    }

    fun cancel() {
        if (!state().running) return
        buildRunCoordinator.cancel()
        buildJob?.cancel()
        updateState {
            copy(
                running = false,
                buildConsole = buildConsole.copy(status = BuildStatus.Cancelled, progressMessage = null),
                snackbarMessage = "Build cancelled",
                bottomPanelTabs = markBuildTab(bottomPanelTabs, error = false, count = null),
            )
        }
    }

    fun confirmInstallConflictUninstall() {
        val packageName = state().installConflict?.applicationId ?: return
        buildRunCoordinator.uninstallConflict(packageName)
        updateState { copy(installConflict = null, snackbarMessage = "Waiting for uninstall confirmation…") }
    }

    fun dismissInstallConflict() {
        updateState {
            copy(
                installConflict = null,
                snackbarMessage = "Install cancelled. The installed app is signed differently.",
            )
        }
    }

    private fun startBuild(variant: String, kind: BuildKind, install: Boolean) {
        if (isBuildInFlight) return
        val root = projectRootPath()?.let { File(it) }
        if (root == null) {
            updateState { copy(snackbarMessage = "Open a project before building") }
            return
        }
        if (networkMonitor?.isOnline() == false) {
            showOfflineBuildFailure()
            return
        }
        if (!buildRunCoordinator.canPostNotifications()) {
            emitEffect(EditorEffect.RequestNotificationsPermission)
        }
        isBuildInFlight = true
        showBuildRunning()
        buildJob = scope.launch {
            try {
                runBuild(root, variant, kind, install)
            } finally {
                isBuildInFlight = false
            }
        }
    }

    private suspend fun runBuild(root: File, variant: String, kind: BuildKind, install: Boolean) {
        if (!prepareWorkspaceForBuild(root)) return
        val targets = resolveBuildTargets(root, variant, kind, install) ?: return
        reportStartResult(buildRunCoordinator.start(buildRequest(root, kind, targets), buildMeta(install)))
    }

    private fun buildRequest(root: File, kind: BuildKind, targets: BuildTargets) = BuildRequest(
        projectRoot = root,
        modulePath = targets.modulePath,
        variantName = targets.variant,
        kind = kind,
        taskPath = targets.taskPath,
        buildType = targets.buildType,
    )

    private fun buildMeta(install: Boolean) = BuildClientMeta(
        projectId = projectId,
        projectName = state().projectName,
        installAfterSuccess = install,
        autoLaunchAfterInstall = shouldLaunchAfterInstall(),
    )

    private fun reportStartResult(result: StartBuildResult) = when (result) {
        is StartBuildResult.Accepted -> Unit
        is StartBuildResult.AlreadyRunning -> showSnackbar(
            if (result.projectId == projectId) "This build is already running" else "Another project is already building",
        )
        is StartBuildResult.Failed -> showStartFailure(result.reason)
    }

    private fun showSnackbar(message: String) = updateState { copy(snackbarMessage = message) }

    private fun showStartFailure(reason: String) = updateState {
        copy(
            snackbarMessage = reason,
            running = false,
            buildFailed = true,
            buildConsole = buildConsole.copy(
                status = BuildStatus.Failed,
                progressMessage = null,
                problems = buildConsole.problems + BuildProblem(BuildEvent.ProblemSeverity.ERROR, reason),
            ),
        )
    }

    private fun showOfflineBuildFailure() {
        val offlineMsg = "You're offline. Connect to the internet and try again."
        updateState {
            copy(
                running = false,
                buildFailed = true,
                bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
                activeBottomTabId = "build",
                buildConsole = BuildConsoleState(
                    status = BuildStatus.Failed,
                    problems = listOf(
                        BuildProblem(
                            severity = BuildEvent.ProblemSeverity.ERROR,
                            message = offlineMsg,
                        ),
                    ),
                ),
                snackbarMessage = offlineMsg,
                bottomPanelTabs = markBuildTab(bottomPanelTabs, error = true, count = 1),
            )
        }
    }

    private fun showBuildRunning() {
        updateState {
            copy(
                running = true,
                buildFailed = false,
                bottomPanelHeightDp = expandedBottomPanelHeight(bottomPanelHeightDp),
                activeBottomTabId = "build",
                buildConsole = BuildConsoleState(status = BuildStatus.Running, progressMessage = "Preparing…"),
            )
        }
    }

    private suspend fun prepareWorkspaceForBuild(root: File): Boolean {
        cancelAutoSave()
        if (!flushDirtyFiles()) {
            failBuildPreparation("Couldn't save pending editor changes before build")
            return false
        }
        val preflight = buildRunCoordinator.preflight(root)
        if (preflight.warnings.isNotEmpty()) applyPreflight(preflight.warnings)
        if (!preflight.canProceed) {
            val blocker = preflight.warnings.first { it.severity == PreflightSeverity.BLOCKER }
            failBuildPreparation(blocker.title, problemCount = 1)
            return false
        }
        buildRunCoordinator.ensureDebugKeystore()
        return true
    }

    private fun failBuildPreparation(message: String, problemCount: Int? = null) {
        updateState {
            copy(
                running = false,
                buildFailed = true,
                buildConsole = buildConsole.copy(status = BuildStatus.Failed, progressMessage = null),
                snackbarMessage = message,
                bottomPanelTabs = markBuildTab(bottomPanelTabs, error = true, count = problemCount),
            )
        }
    }

    private data class BuildTargets(
        val modulePath: String,
        val variant: String,
        val buildType: String?,
        val taskPath: String?,
    )

    private suspend fun resolveBuildTargets(root: File, variant: String, kind: BuildKind, install: Boolean): BuildTargets? {
        val appModule = projectModelFor(root)?.let { RunTargetResolver.resolveAppModule(it) }
        if (appModule == null || appModule.variants.isEmpty()) {
            failBuildPreparation("No supported Android application variants were found. Sync the project and try again.")
            return null
        }
        val resolvedVariant = chosenVariant(appModule, variant, install)
        updateState {
            copy(
                selectedVariant = if (install) resolvedVariant else selectedVariant,
                runModulePath = appModule.path,
                availableVariants = RunTargetResolver.availableVariantNames(appModule),
            )
        }
        val variantModel = appModule.variants.firstOrNull { it.name.equals(resolvedVariant, ignoreCase = true) }
        return BuildTargets(appModule.path, resolvedVariant, variantModel?.buildType, taskPathFor(variantModel, kind))
    }

    private suspend fun projectModelFor(root: File): ProjectModel? = cachedProjectModel
        ?.takeIf { it.first == root.absolutePath }
        ?.second
        ?: runCatching { gradleProjectReader.read(root).model }
            .getOrNull()
            ?.also { cachedProjectModel = root.absolutePath to it }

    private fun chosenVariant(appModule: ModuleModel, variant: String, install: Boolean): String =
        if (!install && variant.equals("release", ignoreCase = true)) {
            RunTargetResolver.resolveReleaseVariant(appModule, state().selectedVariant)
        } else {
            RunTargetResolver.resolveVariant(appModule, variant)
        }

    private fun taskPathFor(variantModel: VariantModel?, kind: BuildKind): String? = when (kind) {
        BuildKind.ASSEMBLE -> variantModel?.assembleTaskPath
        BuildKind.BUNDLE -> variantModel?.bundleTaskPath
        BuildKind.CLEAN -> null
        BuildKind.MODEL -> null
    }

    private fun applyPreflight(warnings: List<PreflightWarning>) {
        val prefix = com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildLogLine(
            text = "Preflight: " + warnings.joinToString("; ") { "${it.title}: ${it.detail}" },
            isError = warnings.any { it.severity == PreflightSeverity.BLOCKER },
        )
        updateState { copy(buildConsole = buildConsole.copy(logs = listOf(prefix) + buildConsole.logs)) }
    }
}

internal fun markBuildTab(
    tabs: List<BottomPanelTabUiModel>,
    error: Boolean,
    count: Int?,
): List<BottomPanelTabUiModel> = tabs.map {
    if (it.id == "build") it.copy(count = count, error = error) else it
}
