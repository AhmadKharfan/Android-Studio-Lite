package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Play-flavor build-system tests that need no Android runtime: static sync via the T8 reader, and the
 * event flow + toolchain gating of [InProcessBuildSystem.build] (short-circuited before any tool runs
 * by a NotReady toolchain, so no network/toolchain is required).
 */
class InProcessBuildSystemTest {

    private lateinit var projectDir: File

    @Before fun setup() {
        projectDir = Files.createTempDirectory("demo-proj").toFile()
        writeAppProject(projectDir)
    }

    private fun system(toolchain: ToolchainProvider): InProcessBuildSystem =
        InProcessBuildSystem(
            reader = GradleProjectReader(),
            toolchainProvider = toolchain,
            mavenCacheDir = File(projectDir, ".cache"),
            buildRootDir = File(projectDir, ".build"),
        )

    @Test fun `sync reads the app module from the static model`() = runTest {
        val model = system(notReady()).sync(projectDir)
        assertEquals("Demo", model.name)
        val app = model.modules.first { it.path == ":app" }
        assertEquals(ModuleType.ANDROID_APP, app.type)
    }

    @Test fun `build gated on missing toolchain fails cleanly with a helpful problem`() = runTest {
        val request = BuildRequest(projectDir, modulePath = ":app", variantName = "debug")
        val events = system(notReady("Install the SDK platform first")).build(request).toList()

        assertTrue("expected a Started event", events.first() is BuildEvent.Started)
        val finished = events.last() as BuildEvent.Finished
        assertFalse("build should fail when the toolchain is missing", finished.success)
        val problem = events.filterIsInstance<BuildEvent.Problem>().first()
        assertEquals(BuildEvent.ProblemSeverity.ERROR, problem.severity)
        assertTrue(problem.message.contains("SDK platform"))
    }

    @Test fun `building a non-existent module fails without crashing`() = runTest {
        val request = BuildRequest(projectDir, modulePath = ":ghost", variantName = "debug")
        val events = system(notReady()).build(request).toList()
        val finished = events.last() as BuildEvent.Finished
        assertFalse(finished.success)
        assertTrue(events.filterIsInstance<BuildEvent.Problem>().any { it.message.contains(":ghost") })
    }

    // ---- helpers ----

    private fun notReady(reason: String = "toolchain not installed"): ToolchainProvider =
        ToolchainProvider { ToolchainStatus.NotReady(reason) }

    private fun writeAppProject(root: File) {
        File(root, "settings.gradle.kts").writeText(
            """
            rootProject.name = "Demo"
            include(":app")
            """.trimIndent(),
        )
        val app = File(root, "app").apply { mkdirs() }
        File(app, "build.gradle.kts").writeText(
            """
            plugins { id("com.android.application") }
            android {
                namespace = "com.demo"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.demo"
                    minSdk = 24
                    targetSdk = 34
                }
            }
            """.trimIndent(),
        )
        File(app, "src/main").apply { mkdirs() }
        File(app, "src/main/AndroidManifest.xml").writeText("<manifest package=\"com.demo\"/>")
    }
}
