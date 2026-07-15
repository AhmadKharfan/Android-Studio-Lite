package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    // --- packageProjectCached: reuse the archive when nothing changed -------------------------

    /** The zip is only rewritten when the source changed; mtime is the proxy for "was rewritten". */
    private fun cachedZip(cache: File) = File(cache, "cached-source.zip")

    private fun newProject(name: String): File = tmp.newFolder(name).also {
        writeFile(it, "settings.gradle.kts", "rootProject.name = \"$name\"")
        writeFile(it, "app/src/main/java/A.kt", "class A")
    }

    @Test
    fun `reuses the archive when the project has not changed`() = runBlocking {
        val project = newProject("unchanged")
        val cache = tmp.newFolder("cache1")

        val first = ProjectPackager().packageProjectCached(project, cache)
        assertTrue(first.isFile)
        // Distinguishes "rewrote the same bytes" from "did not rewrite".
        first.setLastModified(0L)

        val second = ProjectPackager().packageProjectCached(project, cache)
        assertEquals(cachedZip(cache), second)
        assertEquals("archive should have been reused, not re-zipped", 0L, second.lastModified())
    }

    @Test
    fun `re-zips when a source file changes`() = runBlocking {
        val project = newProject("changed")
        val cache = tmp.newFolder("cache2")

        ProjectPackager().packageProjectCached(project, cache)
        cachedZip(cache).setLastModified(0L)

        writeFile(project, "app/src/main/java/A.kt", "class A { val added = 1 }")

        val second = ProjectPackager().packageProjectCached(project, cache)
        assertTrue("archive should have been re-zipped", second.lastModified() > 0L)
        val entries = ZipFile(second).use { zf ->
            zf.getInputStream(zf.getEntry("app/src/main/java/A.kt")).bufferedReader().readText()
        }
        assertTrue("re-zip should carry the new content", entries.contains("added"))
    }

    @Test
    fun `re-zips when a new file appears`() = runBlocking {
        val project = newProject("added-file")
        val cache = tmp.newFolder("cache3")

        ProjectPackager().packageProjectCached(project, cache)
        cachedZip(cache).setLastModified(0L)

        writeFile(project, "app/src/main/java/B.kt", "class B")

        val second = ProjectPackager().packageProjectCached(project, cache)
        assertTrue("a new file must invalidate the archive", second.lastModified() > 0L)
        val entries = ZipFile(second).use { zf -> zf.entries().toList().map { it.name } }
        assertTrue(entries.contains("app/src/main/java/B.kt"))
    }

    @Test
    fun `does not reuse another project's archive`() = runBlocking {
        val cache = tmp.newFolder("cache4")
        val first = newProject("proj-a")
        ProjectPackager().packageProjectCached(first, cache)

        // Same shape and content as proj-a: only the root path differs. Without the root in the
        // fingerprint this would hit proj-a's archive and build the wrong sources.
        val second = tmp.newFolder("proj-b")
        writeFile(second, "settings.gradle.kts", "rootProject.name = \"proj-a\"")
        writeFile(second, "app/src/main/java/A.kt", "class A")
        File(second, "settings.gradle.kts").setLastModified(File(first, "settings.gradle.kts").lastModified())
        File(second, "app/src/main/java/A.kt").setLastModified(File(first, "app/src/main/java/A.kt").lastModified())
        cachedZip(cache).setLastModified(0L)

        val zip = ProjectPackager().packageProjectCached(second, cache)
        assertTrue("a different project must re-zip", zip.lastModified() > 0L)
    }

    @Test
    fun `ignores changes under excluded directories`() = runBlocking {
        val project = newProject("excluded")
        val cache = tmp.newFolder("cache5")

        ProjectPackager().packageProjectCached(project, cache)
        cachedZip(cache).setLastModified(0L)

        // Gradle rewrites these constantly; they are pruned from the zip, so they must not
        // invalidate it — otherwise the cache would never hit in practice.
        writeFile(project, "build/outputs/app.apk", "fresh binary")
        writeFile(project, ".gradle/8.0/fileHashes.bin", "fresh binary")

        val second = ProjectPackager().packageProjectCached(project, cache)
        assertEquals("excluded dirs must not invalidate the archive", 0L, second.lastModified())
    }
}
