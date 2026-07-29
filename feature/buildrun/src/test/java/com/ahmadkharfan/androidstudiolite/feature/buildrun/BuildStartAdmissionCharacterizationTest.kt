package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuild
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildStartAdmissionCharacterizationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `debug build is admitted persisted and started once`() = runBlocking {
        val context = RecordingContext()
        val activeBuildStore = InMemoryActiveBuildRepository()
        val coordinator = coordinator(context, activeBuildStore)

        val first = coordinator.start(request(), meta())
        val second = coordinator.start(request(), meta())

        assertEquals(StartBuildResult.Accepted(OPERATION_ID), first)
        assertEquals(StartBuildResult.AlreadyRunning(OPERATION_ID, PROJECT_ID), second)
        assertEquals(1, context.startedServiceCount)
        assertEquals(OPERATION_ID, activeBuildStore.activeBuild?.operationId)
        assertEquals(PROJECT_ID, coordinator.execution.value.projectId)
        assertEquals(BuildExecutionPhase.Preparing, coordinator.execution.value.phase)
        assertTrue(coordinator.execution.value.active)
    }

    @Test
    fun `release build without signing configuration is rejected before admission`() = runBlocking {
        val context = RecordingContext()
        val activeBuildStore = InMemoryActiveBuildRepository()
        val coordinator = coordinator(context, activeBuildStore)

        val outcome = coordinator.start(request(variant = "release", buildType = "release"), meta())

        assertTrue(outcome is StartBuildResult.Failed)
        assertEquals(0, context.startedServiceCount)
        assertEquals(null, activeBuildStore.activeBuild)
        assertFalse(coordinator.execution.value.active)
    }

    @Test
    fun `service start failure rolls back persisted and in memory admission`() = runBlocking {
        val context = RecordingContext(failStart = true)
        val activeBuildStore = InMemoryActiveBuildRepository()
        val coordinator = coordinator(context, activeBuildStore)

        val outcome = coordinator.start(request(), meta())

        assertEquals(StartBuildResult.Failed("service unavailable"), outcome)
        assertEquals(null, activeBuildStore.activeBuild)
        assertFalse(coordinator.execution.value.active)
        assertEquals(BuildExecutionPhase.Preparing, coordinator.execution.value.phase)
        assertEquals("service unavailable", coordinator.execution.value.console.problems.single().message)
    }

    @Test
    fun `fresh persisted build is adopted as a reconnecting admission`() = runBlocking {
        val context = RecordingContext()
        val activeBuildStore = InMemoryActiveBuildRepository(activeBuild())
        val coordinator = coordinator(context, activeBuildStore)

        val outcome = coordinator.start(request(), meta())

        assertEquals(StartBuildResult.AlreadyRunning(OPERATION_ID, PROJECT_ID), outcome)
        assertEquals(1, context.startedServiceCount)
        assertEquals(BuildExecutionPhase.Reconnecting, coordinator.execution.value.phase)
        assertTrue(coordinator.execution.value.active)
    }

    private fun coordinator(
        context: RecordingContext,
        activeBuildStore: InMemoryActiveBuildRepository,
    ) = BuildRunCoordinator(
        context = context,
        buildSystem = UnusedBuildSystem,
        keystoreManager = MissingReleaseKeystoreManager,
        installOperations = BuildInstallOperations(
            install = { _, _, _, _ -> flowOf() },
            uninstall = { flowOf() },
            cancelActiveInstall = {},
        ),
        gradleReader = GradleProjectReader(),
        notifier = BuildNotifier(context),
        activeBuildStore = activeBuildStore,
        clock = { 1_000L },
        ids = { OPERATION_ID },
    )

    private fun request(
        variant: String = "debug",
        buildType: String? = null,
    ) = BuildRequest(
        projectRoot = temporaryFolder.root,
        modulePath = ":app",
        variantName = variant,
        kind = BuildKind.ASSEMBLE,
        buildType = buildType,
    )

    private fun meta() = BuildClientMeta(
        projectId = PROJECT_ID,
        projectName = "Example",
        installAfterSuccess = false,
    )

    private fun activeBuild() = ActiveBuild(
        buildId = "build-1",
        operationId = OPERATION_ID,
        projectId = PROJECT_ID,
        projectRootPath = temporaryFolder.root.absolutePath,
        projectName = "Example",
        installAfterSuccess = false,
        autoLaunchAfterInstall = true,
        startedAtEpochMs = 1_000L,
        modulePath = ":app",
        variantName = "debug",
        kind = BuildKind.ASSEMBLE.name,
    )

    private class RecordingContext(private val failStart: Boolean = false) : ContextWrapper(null) {
        var startedServiceCount = 0

        override fun startService(service: Intent?): ComponentName? {
            startedServiceCount++
            if (failStart) error("service unavailable")
            return null
        }
    }

    private class InMemoryActiveBuildRepository(
        var activeBuild: ActiveBuild? = null,
    ) : ActiveBuildRepository {

        override fun observe(): Flow<ActiveBuild?> = flowOf(activeBuild)
        override suspend fun get(): ActiveBuild? = activeBuild
        override suspend fun save(build: ActiveBuild) {
            activeBuild = build
        }
        override suspend fun clear(buildId: String?) {
            activeBuild = null
        }
    }

    private object UnusedBuildSystem : BuildSystem {
        override fun build(request: BuildRequest): Flow<BuildEvent> = unexpectedCall()
        override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = unexpectedCall()
        override suspend fun sync(projectRoot: File): ProjectModel = unexpectedCall()
        override fun cancel() = unexpectedCall()
    }

    private object MissingReleaseKeystoreManager : KeystoreManager {
        override suspend fun releaseSigningConfig(): SigningConfig? = null
        override fun debugKeystoreFile(): File = unexpectedCall()
        override fun suggestedReleaseKeystoreFile(): File = unexpectedCall()
        override suspend fun debugSigningConfig(): SigningConfig = unexpectedCall()
        override suspend fun createReleaseKeystore(params: ReleaseKeystoreParams): SigningConfig = unexpectedCall()
        override suspend fun importReleaseKeystore(
            storeFile: File,
            storePassword: String,
            keyAlias: String,
            keyPassword: String,
        ): SigningConfig = unexpectedCall()
        override suspend fun clearReleaseKeystore() = unexpectedCall()
        override suspend fun signingConfigFor(buildType: String): SigningConfig = unexpectedCall()
    }

    private companion object {
        const val OPERATION_ID = "operation-1"
        const val PROJECT_ID = "project-1"

        fun unexpectedCall(): Nothing = error("Unexpected call")
    }
}
