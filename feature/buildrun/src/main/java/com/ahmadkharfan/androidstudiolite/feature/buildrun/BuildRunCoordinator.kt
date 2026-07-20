package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.Context
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
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

    override fun build(request: BuildRequest, meta: BuildClientMeta): Flow<BuildEvent> {
        RemoteBuildKeepAliveService.startBuilding(context, meta.projectId, meta.projectName, "Preparing…")
        return buildSystem.build(request)
            .onEach { event -> onBuildEvent(event, meta, request.projectRoot) }

    }

    override suspend fun attachIfActive(projectId: String, projectRoot: File, projectName: String): Flow<BuildEvent>? {
        val active = activeBuildStore.get() ?: return null
        if (active.projectId != projectId) return null

        if (System.currentTimeMillis() - active.startedAtEpochMs > ACTIVE_BUILD_MAX_AGE_MS) {
            activeBuildStore.clear(active.buildId)
            return null
        }
        val meta = BuildClientMeta(
            projectId = projectId,
            projectName = projectName.ifBlank { active.projectName },
            installAfterSuccess = active.installAfterSuccess,
        )
        RemoteBuildKeepAliveService.startBuilding(
            context,
            meta.projectId,
            meta.projectName,
            "Reconnecting…",
        )
        return buildSystem.attach(active.buildId, projectRoot)
            .onEach { event -> onBuildEvent(event, meta, projectRoot) }
    }

    override fun updateKeepAliveProgress(projectId: String, projectName: String, progress: String) {
        RemoteBuildKeepAliveService.updateProgress(context, projectId, projectName, progress)
    }

    override fun endKeepAlive() = demoteKeepAlive()

    override suspend fun activeBuildFor(projectId: String): ActiveBuild? {
        val active = activeBuildStore.get() ?: return null
        if (active.projectId != projectId) return null
        if (System.currentTimeMillis() - active.startedAtEpochMs > ACTIVE_BUILD_MAX_AGE_MS) {
            activeBuildStore.clear(active.buildId)
            return null
        }
        return active
    }

    override fun cancel() {
        buildSystem.cancel()
        demoteKeepAlive()
        clearScope.launch { activeBuildStore.clear() }
    }

    override suspend fun resolveApplicationId(projectRoot: File, modulePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val modules = gradleReader.read(projectRoot).model.modules
                val module = modules.firstOrNull { it.path == modulePath }
                    ?: modules.firstOrNull { it.type == ModuleType.ANDROID_APP }
                module?.applicationId
            }.getOrNull()
        }

    override fun install(apk: File, applicationId: String?, autoLaunch: Boolean): Flow<InstallEvent> =
        apkInstaller.install(apk, applicationId, autoLaunch)

    override fun uninstall(applicationId: String): Flow<UninstallEvent> = apkInstaller.uninstall(applicationId)

    override fun notifyFinished(
        projectName: String,
        success: Boolean,
        durationMillis: Long?,
        projectId: String,
        installFollows: Boolean,
    ) = notifier.notifyFinished(projectName, success, durationMillis, projectId, installFollows)

    override fun canPostNotifications(): Boolean = notifier.canPost()

    private suspend fun onBuildEvent(event: BuildEvent, meta: BuildClientMeta, projectRoot: File) {
        when (event) {
            is BuildEvent.RemoteBuildBound -> {
                val existing = activeBuildStore.get()
                activeBuildStore.save(
                    ActiveBuild(
                        buildId = event.buildId,
                        projectId = meta.projectId,
                        projectRootPath = projectRoot.absolutePath,
                        projectName = meta.projectName,
                        installAfterSuccess = meta.installAfterSuccess,
                        startedAtEpochMs = existing
                            ?.takeIf { it.buildId == event.buildId }
                            ?.startedAtEpochMs
                            ?: System.currentTimeMillis(),
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
            is BuildEvent.Finished -> {
                activeBuildStore.clear()
            }
            else -> Unit
        }
    }

    private fun demoteKeepAlive() {
        RemoteBuildKeepAliveService.stopBuilding(context)
    }

    private companion object {
        const val TOOLCHAIN_JDK_MAJOR = 17
        val AGP_VERSION_KEYS = listOf("agp", "androidGradlePlugin", "android-gradle-plugin", "android")
        const val ACTIVE_BUILD_MAX_AGE_MS = 1_000_000L
    }
}
