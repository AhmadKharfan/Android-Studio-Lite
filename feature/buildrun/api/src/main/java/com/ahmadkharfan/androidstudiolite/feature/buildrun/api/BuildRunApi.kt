package com.ahmadkharfan.androidstudiolite.feature.buildrun.api

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.BuildPreflightResult
import java.io.File
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import kotlinx.coroutines.flow.StateFlow

public data class BuildExecutionSnapshot(
    val operationId: String? = null,
    val projectId: String? = null,
    val projectName: String = "",
    val console: BuildConsoleState = BuildConsoleState(),
    val installRequested: Boolean = false,
    val installState: InstallExecutionState = InstallExecutionState.None,
    val installConflictPackage: String? = null,
    val installFailureReason: String? = null,
    val active: Boolean = false,
    val phase: BuildExecutionPhase = BuildExecutionPhase.Idle,
) {
    val isActive: Boolean get() = active
}

public enum class InstallExecutionState { None, Preparing, AwaitingConfirmation, Installed, Failed }

public enum class BuildExecutionPhase {
    Idle, Preparing, Running, Reconnecting, DownloadingArtifact, Installing,
    AwaitingInstallConfirmation, Succeeded, Failed, Cancelling, Cancelled, TimedOut,
}

public sealed interface StartBuildResult {
    public data class Accepted(val operationId: String) : StartBuildResult
    public data class AlreadyRunning(val operationId: String, val projectId: String?) : StartBuildResult
    public data class Failed(val reason: String) : StartBuildResult
}

public data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
)

public interface BuildRunApi {
    public val execution: StateFlow<BuildExecutionSnapshot>
    public suspend fun preflight(projectRoot: File): BuildPreflightResult
    public suspend fun syncProject(projectRoot: File): ProjectModel
    public suspend fun ensureDebugKeystore()
    public suspend fun start(request: BuildRequest, meta: BuildClientMeta): StartBuildResult
    public suspend fun recover(projectId: String): Boolean
    public fun cancel()
    public fun uninstallConflict(packageName: String)
    public fun canPostNotifications(): Boolean
}
