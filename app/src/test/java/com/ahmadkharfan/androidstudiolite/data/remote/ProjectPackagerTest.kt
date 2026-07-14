package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verifies [ProjectPackager] zips a project's real sources with project-relative POSIX entry names
 * and prunes the build/tooling scratch directories that would only bloat the upload.
 */
class ProjectPackagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun writeFile(root: File, path: String, text: String = "x") {
        val f = File(root, path)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }

    @Test
    fun `zips sources and excludes build gradle idea git`() = runBlocking {
        val project = tmp.newFolder("proj")
        writeFile(project, "settings.gradle.kts", "rootProject.name = \"proj\"")
        writeFile(project, "app/build.gradle.kts", "plugins {}")
        writeFile(project, "app/src/main/java/A.kt", "class A")
        writeFile(project, "app/src/main/AndroidManifest.xml", "<manifest/>")
        // Excluded scratch/tooling dirs, at various depths:
        writeFile(project, "build/outputs/app.apk", "binary")
        writeFile(project, "app/build/intermediates/x.bin", "binary")
        writeFile(project, ".gradle/8.0/fileHashes.bin", "binary")
        writeFile(project, ".idea/workspace.xml", "<x/>")
        writeFile(project, ".git/HEAD", "ref: refs/heads/main")

        val dest = File(tmp.root, "out/source.zip")
        ProjectPackager().packageProject(project, dest)

        assertTrue("zip should exist", dest.isFile)
        val entries = ZipFile(dest).use { zf -> zf.entries().toList().map { it.name } }

        assertTrue(entries.contains("settings.gradle.kts"))
        assertTrue(entries.contains("app/build.gradle.kts"))
        assertTrue(entries.contains("app/src/main/java/A.kt"))
        assertTrue(entries.contains("app/src/main/AndroidManifest.xml"))

        assertFalse(entries.any { it.startsWith("build/") })
        assertFalse(entries.any { it.contains("/build/") })
        assertFalse(entries.any { it.startsWith(".gradle/") })
        assertFalse(entries.any { it.startsWith(".idea/") })
        assertFalse(entries.any { it.startsWith(".git/") })

        // Entry names use POSIX separators regardless of platform.
        assertFalse(entries.any { it.contains('\\') })
    }

    @Test
    fun `preserves file contents`() = runBlocking {
        val project = tmp.newFolder("proj2")
        writeFile(project, "settings.gradle.kts", "rootProject.name = \"proj2\"")
        writeFile(project, "app/src/main/java/Main.kt", "fun main() = Unit")

        val dest = File(tmp.root, "out2/source.zip")
        ProjectPackager().packageProject(project, dest)

        val content = ZipFile(dest).use { zf ->
            val entry = zf.getEntry("app/src/main/java/Main.kt")
            zf.getInputStream(entry).bufferedReader().readText()
        }
        assertTrue(content.contains("fun main"))
    }
}
