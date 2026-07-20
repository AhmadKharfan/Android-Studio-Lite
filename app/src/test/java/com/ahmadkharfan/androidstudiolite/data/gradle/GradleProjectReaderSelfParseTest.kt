package com.ahmadkharfan.androidstudiolite.data.gradle

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class GradleProjectReaderSelfParseTest {

    private val reader = GradleProjectReader()

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


        val app = result.model.modules.firstOrNull { it.path == ":app" }
        assertTrue("Expected an :app module, got ${result.model.modules.map { it.path }}", app != null)
        requireNotNull(app)


        val coords = app.dependencies.mapNotNull { it.coordinate }
        assertTrue("Expected okhttp coordinate resolved, got $coords",
            coords.any { it.startsWith("com.squareup.okhttp3:okhttp:") })


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

            assertTrue("Expected modules in ${project.name}", result.model.modules.isNotEmpty())
        }
    }
}
