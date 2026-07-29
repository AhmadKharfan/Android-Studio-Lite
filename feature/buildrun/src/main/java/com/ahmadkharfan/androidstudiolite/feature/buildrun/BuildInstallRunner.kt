package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.id.IdGenerator
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal class BuildInstallOperations(
    val install: (
        apk: File,
        applicationId: String?,
        autoLaunch: Boolean,
        requestToken: String,
    ) -> Flow<InstallEvent>,
    val uninstall: (packageName: String) -> Flow<UninstallEvent>,
    val cancelActiveInstall: () -> Unit,
)

internal class BuildInstallRunner(
    private val operations: BuildInstallOperations,
    private val gradleReader: GradleProjectInspector,
    private val ids: IdGenerator,
) {
    suspend fun install(
        request: BuildInstallRequest,
        onInstalling: () -> Unit,
        onEvent: (InstallEvent) -> Unit,
    ): Boolean {
        val artifact = request.console.artifact
        val apk = artifact?.takeIf { it.kind == BuildEvent.ArtifactKind.APK }?.let { File(it.path) }
        if (apk == null || !apk.isFile) return false

        val applicationId = resolveApplicationId(request.buildRequest.projectRoot, request.buildRequest.modulePath)
        onInstalling()
        withTimeoutOrNull(INSTALL_OBSERVER_TIMEOUT_MS.milliseconds) {
            operations.install(
                apk,
                applicationId,
                request.autoLaunch,
                request.operationId ?: ids.newId(),
            ).collect(onEvent)
        }
        return true
    }

    private suspend fun resolveApplicationId(projectRoot: File, modulePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val modules = gradleReader.inspect(projectRoot).model.modules
                val module = modules.firstOrNull { it.path == modulePath }
                    ?: modules.firstOrNull { it.type == ModuleType.ANDROID_APP }
                module?.applicationId
            }.getOrNull()
        }

    private companion object {
        const val INSTALL_OBSERVER_TIMEOUT_MS = 600_000L
    }
}

internal data class BuildInstallRequest(
    val console: BuildConsoleState,
    val buildRequest: BuildRequest,
    val autoLaunch: Boolean,
    val operationId: String?,
)

internal fun BuildExecutionSnapshot.reduceInstallEvent(event: InstallEvent): BuildExecutionSnapshot = when (event) {
    is InstallEvent.Preparing -> copy(
        installState = InstallExecutionState.Preparing,
        phase = BuildExecutionPhase.Installing,
    )
    is InstallEvent.AwaitingConfirmation -> copy(
        installState = InstallExecutionState.AwaitingConfirmation,
        phase = BuildExecutionPhase.AwaitingInstallConfirmation,
    )
    is InstallEvent.Installed -> copy(
        installState = InstallExecutionState.Installed,
        phase = BuildExecutionPhase.Succeeded,
    )
    is InstallEvent.Conflict -> copy(
        installState = InstallExecutionState.Failed,
        phase = BuildExecutionPhase.Failed,
        installConflictPackage = event.packageName,
        installFailureReason = event.reason,
    )
    is InstallEvent.Failed -> copy(
        installState = InstallExecutionState.Failed,
        phase = BuildExecutionPhase.Failed,
        installFailureReason = event.reason,
    )
}
