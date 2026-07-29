package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.content.ContextWrapper
import com.ahmadkharfan.androidstudiolite.feature.buildrun.install.InstallEvent
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildInstallOrchestrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `successful install events advance execution through current phases`() = runBlocking {
        val snapshots = mutableListOf<Pair<InstallExecutionState, BuildExecutionPhase>>()
        lateinit var coordinator: BuildRunCoordinator
        val operations = BuildInstallOperations(
            install = { apk, applicationId, autoLaunch, requestToken ->
                assertEquals("app.apk", apk.name)
                assertEquals(null, applicationId)
                assertFalse(autoLaunch)
                assertEquals(OPERATION_ID, requestToken)
                flow {
                    emit(InstallEvent.Preparing)
                    snapshots += coordinator.installSnapshot()
                    emit(InstallEvent.AwaitingConfirmation)
                    snapshots += coordinator.installSnapshot()
                    emit(InstallEvent.Installed("com.example.app", launched = false))
                    snapshots += coordinator.installSnapshot()
                }
            },
            uninstall = { unexpectedCall() },
            cancelActiveInstall = { unexpectedCall() },
        )
        coordinator = coordinator(operations)

        coordinator.execute(OPERATION_ID, request(), meta())

        assertEquals(
            listOf(
                InstallExecutionState.Preparing to BuildExecutionPhase.Installing,
                InstallExecutionState.AwaitingConfirmation to BuildExecutionPhase.AwaitingInstallConfirmation,
                InstallExecutionState.Installed to BuildExecutionPhase.Succeeded,
            ),
            snapshots,
        )
        assertEquals(InstallExecutionState.Installed, coordinator.execution.value.installState)
        assertEquals(BuildExecutionPhase.Succeeded, coordinator.execution.value.phase)
        assertFalse(coordinator.execution.value.active)
    }

    @Test
    fun `install conflict preserves package and reason in failed execution`() = runBlocking {
        val operations = BuildInstallOperations(
            install = { _, _, _, _ ->
                flowOf(InstallEvent.Conflict("com.example.app", "signature mismatch"))
            },
            uninstall = { unexpectedCall() },
            cancelActiveInstall = { unexpectedCall() },
        )
        val coordinator = coordinator(operations)

        coordinator.execute(OPERATION_ID, request(), meta())

        val execution = coordinator.execution.value
        assertEquals(InstallExecutionState.Failed, execution.installState)
        assertEquals(BuildExecutionPhase.Failed, execution.phase)
        assertEquals("com.example.app", execution.installConflictPackage)
        assertEquals("signature mismatch", execution.installFailureReason)
        assertFalse(execution.active)
    }

    private fun coordinator(operations: BuildInstallOperations): BuildRunCoordinator {
        val context = ContextWrapper(null)
        return BuildRunCoordinator(
            context = context,
            buildSystem = SuccessfulBuildSystem(apk()),
            keystoreManager = UnusedKeystoreManager,
            installOperations = operations,
            gradleReader = GradleProjectReader(),
            notifier = BuildNotifier(context),
            activeBuildStore = InMemoryActiveBuildRepository(activeBuild()),
            clock = { 1_000L },
            ids = { "generated-id" },
        )
    }

    private fun BuildRunCoordinator.installSnapshot() =
        execution.value.installState to execution.value.phase

    private fun request() = BuildRequest(
        projectRoot = temporaryFolder.newFolder("project"),
        modulePath = ":app",
        variantName = "debug",
        kind = BuildKind.ASSEMBLE,
        operationId = OPERATION_ID,
    )

    private fun meta() = BuildClientMeta(
        projectId = PROJECT_ID,
        projectName = "Example",
        installAfterSuccess = true,
        autoLaunchAfterInstall = false,
    )

    private fun apk(): File = temporaryFolder.newFile("app.apk").apply { writeText("apk") }

    private fun activeBuild() = ActiveBuild(
        buildId = "",
        operationId = OPERATION_ID,
        projectId = PROJECT_ID,
        projectRootPath = temporaryFolder.root.absolutePath,
        projectName = "Example",
        installAfterSuccess = true,
        autoLaunchAfterInstall = false,
        startedAtEpochMs = 1_000L,
        modulePath = ":app",
        variantName = "debug",
        kind = BuildKind.ASSEMBLE.name,
    )

    private class SuccessfulBuildSystem(private val apk: File) : BuildSystem {
        override fun build(request: BuildRequest): Flow<BuildEvent> = flowOf(
            BuildEvent.ArtifactProduced(apk, BuildEvent.ArtifactKind.APK),
            BuildEvent.Finished(success = true, durationMillis = 42L),
        )

        override suspend fun sync(projectRoot: File): ProjectModel = unexpectedCall()
        override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = unexpectedCall()
        override fun cancel() = unexpectedCall()
    }

    private class InMemoryActiveBuildRepository(
        private var activeBuild: ActiveBuild?,
    ) : ActiveBuildRepository {
        override fun observe(): Flow<ActiveBuild?> = flowOf(activeBuild)
        override suspend fun get(): ActiveBuild? = activeBuild
        override suspend fun save(build: ActiveBuild) {
            activeBuild = build
        }
        override suspend fun clear(buildId: String?) {
            if (buildId == null || activeBuild?.buildId == buildId) activeBuild = null
        }
    }

    private object UnusedKeystoreManager : KeystoreManager {
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
        override suspend fun releaseSigningConfig(): SigningConfig? {
            unexpectedCall()
        }
        override suspend fun clearReleaseKeystore() = unexpectedCall()
        override suspend fun signingConfigFor(buildType: String): SigningConfig = unexpectedCall()
    }

    private companion object {
        const val OPERATION_ID = "operation-1"
        const val PROJECT_ID = "project-1"

        fun unexpectedCall(): Nothing = error("Unexpected call")
    }
}
