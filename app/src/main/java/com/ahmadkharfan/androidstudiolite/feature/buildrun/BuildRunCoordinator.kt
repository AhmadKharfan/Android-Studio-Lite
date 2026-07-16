package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.Context
import com.ahmadkharfan.androidstudiolite.data.buildsystem.FakeBuildSystem
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The single, flavor-agnostic entry point the UI uses to build → install → run. It composes the
 * bound [BuildSystem] (the play/full backend, or [com.ahmadkharfan.androidstudiolite.data.buildsystem.FakeBuildSystem]
 * until they land) with the reliability preflight, keystore management, APK installation, and
 * finish-notification — none of which depend on which backend is bound.
 */
class BuildRunCoordinator(
    private val context: Context,
    private val buildSystem: BuildSystem,
    private val keystoreManager: KeystoreManager,
    private val apkInstaller: ApkInstaller,
    private val gradleReader: GradleProjectReader,
    private val notifier: BuildNotifier,
) {

    /** Runs the pre-build reliability checks (doc 10 §3): storage pressure + version compatibility. */
    suspend fun preflight(projectRoot: File): BuildPreflightResult = withContext(Dispatchers.IO) {
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
    suspend fun ensureDebugKeystore() {
        runCatching { keystoreManager.debugSigningConfig() }
    }

    fun build(request: BuildRequest): Flow<BuildEvent> = buildSystem.build(request)

    fun cancel() = buildSystem.cancel()

    /**
     * Dev-only: makes the next build fail, for exercising the failure UI. No-ops unless the temporary
     * [FakeBuildSystem] is bound (the real backends have nothing to simulate).
     */
    fun simulateNextFailure() {
        (buildSystem as? FakeBuildSystem)?.failNextBuild = true
    }

    /**
     * The applicationId [modulePath] installs as, read straight off the build script, or null when the
     * project can't be parsed / declares none. The build backend reports the APK's path but not its
     * package, so this is what lets the install flow name — and, on a signature conflict, uninstall —
     * the app being replaced.
     */
    suspend fun resolveApplicationId(projectRoot: File, modulePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val modules = gradleReader.read(projectRoot).model.modules
                val module = modules.firstOrNull { it.path == modulePath }
                    ?: modules.firstOrNull { it.type == ModuleType.ANDROID_APP }
                module?.applicationId
            }.getOrNull()
        }

    fun install(apk: File, applicationId: String?, autoLaunch: Boolean): Flow<InstallEvent> =
        apkInstaller.install(apk, applicationId, autoLaunch)

    /** Removes [applicationId] (and its data) so a differently-signed rebuild can install. */
    fun uninstall(applicationId: String): Flow<UninstallEvent> = apkInstaller.uninstall(applicationId)

    fun notifyFinished(projectName: String, success: Boolean, durationMillis: Long?) =
        notifier.notifyFinished(projectName, success, durationMillis)

    private companion object {
        /** The full flavor ships OpenJDK 17; the play engine targets the same source/target level. */
        const val TOOLCHAIN_JDK_MAJOR = 17
        val AGP_VERSION_KEYS = listOf("agp", "androidGradlePlugin", "android-gradle-plugin", "android")
    }
}
