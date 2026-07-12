package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Parses real Gradle projects on disk: this repository itself (always available) and — best-effort,
 * skipped when absent — a couple of sibling public Android checkouts. Guards our tolerance against
 * genuine build scripts rather than only synthetic fixtures.
 */
class GradleProjectReaderSelfParseTest {

    private val reader = GradleProjectReader()

    /** Walk up from the test's working directory to the ASL repo root (has settings + our appId). */
    private fun aslRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val settings = File(dir, "settings.gradle.kts")
            if (settings.isFile && settings.readText().contains("AndroidStudioLite")) return dir
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun parsesThisRepository() {
        val root = aslRepoRoot()
        assumeTrue("ASL repo root not found from ${File("").absolutePath}", root != null)
        requireNotNull(root)

        val result = reader.read(root)
        // The :app module is an Android application with our namespace and the play/full flavors.
        val app = result.model.modules.firstOrNull { it.path == ":app" }
        assertTrue("Expected an :app module, got ${result.model.modules.map { it.path }}", app != null)
        requireNotNull(app)
        assertTrue(app.type == ModuleType.ANDROID_APP)

        val flavorNames = app.variants.flatMap { it.flavors }.toSet()
        assertTrue("Expected play/full flavors, got ${app.variants.map { it.name }}",
            flavorNames.containsAll(listOf("play", "full")))

        // Catalog references resolve to real coordinates.
        val coords = app.dependencies.mapNotNull { it.coordinate }
        assertTrue("Expected okhttp coordinate resolved, got $coords",
            coords.any { it.startsWith("com.squareup.okhttp3:okhttp:") })

        // A version catalog and a Gradle wrapper version were both discovered.
        assertTrue(result.catalog != null)
        assertTrue(result.gradleVersion != null)
    }

    @Test
    fun parsesSiblingPublicProjectsWhenPresent() {
        val root = aslRepoRoot() ?: return
        val siblingsParent = root.parentFile ?: return
        val candidates = listOf("element-x-android", "android-code-studio")
            .map { File(siblingsParent, it) }
            .filter { reader.isGradleProject(it) }
        assumeTrue("No sibling public Gradle projects on disk", candidates.isNotEmpty())

        for (project in candidates) {
            val result = reader.read(project)
            // Tolerance contract: we always produce a model with at least one module and never throw.
            assertTrue("Expected modules in ${project.name}", result.model.modules.isNotEmpty())
        }
    }
}
