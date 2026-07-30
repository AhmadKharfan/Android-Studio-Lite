package com.ahmadkharfan.androidstudiolite.feature.buildrun.api

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.BuildPreflightResult
import java.io.File
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import kotlinx.coroutines.flow.StateFlow

data class BuildExecutionSnapshot(
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

enum class InstallExecutionState { None, Preparing, AwaitingConfirmation, Installed, Failed }

enum class BuildExecutionPhase {
    Idle, Preparing, Running, Reconnecting, DownloadingArtifact, Installing,
    AwaitingInstallConfirmation, Succeeded, Failed, Cancelling, Cancelled, TimedOut,
}

sealed interface StartBuildResult {
    data class Accepted(val operationId: String) : StartBuildResult
    data class AlreadyRunning(val operationId: String, val projectId: String?) : StartBuildResult
    data class Failed(val reason: String) : StartBuildResult
}

data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
)

interface BuildRunApi {
    val execution: StateFlow<BuildExecutionSnapshot>
    suspend fun preflight(projectRoot: File): BuildPreflightResult
    suspend fun syncProject(projectRoot: File): ProjectModel
    suspend fun ensureDebugKeystore()
    suspend fun start(request: BuildRequest, meta: BuildClientMeta): StartBuildResult
    suspend fun recover(projectId: String): Boolean
    fun cancel()
    fun uninstallConflict(packageName: String)
    fun canPostNotifications(): Boolean
}
