package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectSummary
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildClientMeta
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildExecutionSnapshot
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.StartBuildResult
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.BuildPreflightResult
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.PreflightSeverity
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.PreflightWarning
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Run when the build service blocks it: the Cloud Build setup opens, and its retry runs the same build. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorBuildSetupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeBuildRun(var preflight: BuildPreflightResult) : BuildRunApi {
        val started = mutableListOf<BuildRequest>()
        override val execution = MutableStateFlow(BuildExecutionSnapshot())
        override suspend fun preflight(projectRoot: File) = preflight
        override suspend fun syncProject(projectRoot: File) = error("not used")
        override suspend fun ensureDebugKeystore() = Unit
        override suspend fun start(request: BuildRequest, meta: BuildClientMeta): StartBuildResult {
            started += request
            return StartBuildResult.Accepted("op-1")
        }
        override suspend fun recover(projectId: String) = false
        override fun cancel() = Unit
        override fun uninstallConflict(packageName: String) = Unit
        override fun canPostNotifications() = true
    }

    private fun blocked(fromBuildService: Boolean) = BuildPreflightResult(
        listOf(PreflightWarning(PreflightSeverity.BLOCKER, "Build setup required", "Allow access", fromBuildService)),
    )

    private fun TestScope.controller(buildRun: FakeBuildRun, retries: MutableList<() -> Unit>): EditorBuildController {
        val root = tmp.newFolder("MyApp")
        val model = ProjectModel(
            name = "MyApp",
            rootDir = root,
            modules = listOf(
                ModuleModel(
                    path = ":app",
                    name = "app",
                    type = ModuleType.ANDROID_APP,
                    moduleDir = File(root, "app"),
                    variants = listOf(VariantModel("debug", "debug", assembleTaskPath = ":app:assembleDebug")),
                ),
            ),
        )
        var state = EditorUiState(selectedVariant = "debug")
        return EditorBuildController(
            projectId = "p1",
            scope = this,
            buildRunCoordinator = buildRun,
            gradleProjectReader = object : GradleProjectInspector {
                override fun isGradleProject(dir: File) = true
                override fun inspect(projectRoot: File) = GradleProjectSummary(model)
            },
            networkMonitor = null,
            projectRootPath = { root.absolutePath },
            state = { state },
            updateState = { state = state.it() },
            emitEffect = {},
            shouldLaunchAfterInstall = { true },
            cancelAutoSave = {},
            flushDirtyFiles = { true },
            openBuildServiceSetup = { retry -> retries += retry },
        )
    }

    @Test
    fun `a build service blocker opens the setup, and its retry runs the build`() = runTest(UnconfinedTestDispatcher()) {
        val buildRun = FakeBuildRun(blocked(fromBuildService = true))
        val retries = mutableListOf<() -> Unit>()
        val controller = controller(buildRun, retries)

        controller.run()
        assertEquals(1, retries.size)
        assertTrue(buildRun.started.isEmpty())

        buildRun.preflight = BuildPreflightResult(emptyList())
        retries.single().invoke()

        assertEquals(":app:assembleDebug", buildRun.started.single().taskPath)
    }

    @Test
    fun `a project blocker doesn't open the setup`() = runTest(UnconfinedTestDispatcher()) {
        val buildRun = FakeBuildRun(blocked(fromBuildService = false))
        val retries = mutableListOf<() -> Unit>()

        controller(buildRun, retries).run()

        assertTrue(retries.isEmpty())
        assertTrue(buildRun.started.isEmpty())
    }

    @Test
    fun `a ready build service builds without any setup`() = runTest(UnconfinedTestDispatcher()) {
        val buildRun = FakeBuildRun(BuildPreflightResult(emptyList()))
        val retries = mutableListOf<() -> Unit>()

        controller(buildRun, retries).run()

        assertTrue(retries.isEmpty())
        assertEquals(1, buildRun.started.size)
    }
}
