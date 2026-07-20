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

/**
 * Client-side context for a build/run so the coordinator can persist, keep the process alive, and
 * notify with a tap target back to the editor.
 */
data class BuildClientMeta(
    val projectId: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
)

/**
 * The single entry point the UI uses to build → install → run. It composes the bound [BuildSystem]
 * (the remote build backend) with the reliability preflight, keystore management, APK installation,
 * active-build persistence, foreground keep-alive, and finish-notification.
 */
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

    /** Runs the pre-build reliability checks (doc 10 §3): storage pressure + version compatibility. */
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

    /** Ensures the debug keystore exists so debug builds are signable before the build starts. */
    override suspend fun ensureDebugKeystore() {
        runCatching { keystoreManager.debugSigningConfig() }
    }

    override fun build(request: BuildRequest, meta: BuildClientMeta): Flow<BuildEvent> {
        RemoteBuildKeepAliveService.startBuilding(context, meta.projectId, meta.projectName, "Preparing…")
        return buildSystem.build(request)
            .onEach { event -> onBuildEvent(event, meta, request.projectRoot) }
        // Keep FGS up through install — demote via [endKeepAlive] after install finishes.
    }

    /**
     * Reattaches to a persisted in-flight remote build for [meta.projectId], or null when none matches.
     */
    override suspend fun attachIfActive(projectId: String, projectRoot: File, projectName: String): Flow<BuildEvent>? {
        val active = activeBuildStore.get() ?: return null
        if (active.projectId != projectId) return null
        // Stale entry older than the follow budget — drop it.
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

    /** Updates the ongoing keep-alive notification (e.g. while installing). */
    override fun updateKeepAliveProgress(projectId: String, projectName: String, progress: String) {
        RemoteBuildKeepAliveService.updateProgress(context, projectId, projectName, progress)
    }

    /** Drops the foreground keep-alive after build + optional install complete. */
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

    /**
     * The applicationId [modulePath] installs as, read straight off the build script, or null when the
     * project can't be parsed / declares none. The build backend reports the APK's path but not its
     * package, so this is what lets the install flow name — and, on a signature conflict, uninstall —
     * the app being replaced.
     */
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

    /** Removes [applicationId] (and its data) so a differently-signed rebuild can install. */
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
        /** The remote build worker ships OpenJDK 17; preflight checks compatibility against it. */
        const val TOOLCHAIN_JDK_MAJOR = 17
        val AGP_VERSION_KEYS = listOf("agp", "androidGradlePlugin", "android-gradle-plugin", "android")
        /** Align with RemoteBuildSystem follow budget (15 min) plus a little slack. */
        const val ACTIVE_BUILD_MAX_AGE_MS = 1_000_000L
    }
}
