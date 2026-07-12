package com.ahmadkharfan.androidstudiolite.data.environment

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * JVM tests for the component-archive extractor. Symlinks are created through
 * `java.nio.file.Files` here (the production wiring injects `android.system.Os.symlink`).
 */
class TarXzExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val extractor = TarXzExtractor { target, link ->
        Files.createSymbolicLink(link.toPath(), Paths.get(target))
    }

    // ---- archive-building helpers ----------------------------------------------------------

    private fun archive(build: TarArchiveOutputStream.() -> Unit): File {
        val file = tmp.newFile("archive-${System.nanoTime()}.tar.xz")
        XZCompressorOutputStream(file.outputStream()).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                tar.build()
            }
        }
        return file
    }

    private fun TarArchiveOutputStream.file(name: String, content: String, mode: Int = "644".toInt(8)) {
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = mode
        putArchiveEntry(entry)
        write(bytes)
        closeArchiveEntry()
    }

    private fun TarArchiveOutputStream.dir(name: String) {
        putArchiveEntry(TarArchiveEntry(if (name.endsWith("/")) name else "$name/"))
        closeArchiveEntry()
    }

    private fun TarArchiveOutputStream.symlink(name: String, target: String) {
        val entry = TarArchiveEntry(name, TarConstants.LF_SYMLINK)
        entry.linkName = target
        putArchiveEntry(entry)
        closeArchiveEntry()
    }

    private fun isSymlinkOnDisk(file: File) = Files.isSymbolicLink(file.toPath())

    // ---- defect 1: symlink entries ---------------------------------------------------------

    @Test
    fun `symlink entries become real symlinks, not empty files`() {
        val target = tmp.newFolder("jdk-target")
        extractor.extract(
            archive {
                dir("lib")
                file("lib/libjava.so.17", "elf-bytes")
                symlink("lib/libjava.so", "libjava.so.17")
            },
            target,
        )

        val link = File(target, "lib/libjava.so")
        assertTrue("link should be a symlink", isSymlinkOnDisk(link))
        assertEquals("libjava.so.17", Files.readSymbolicLink(link.toPath()).toString())
        assertEquals("elf-bytes", link.readText()) // resolves through the link
    }

    @Test
    fun `symlink may precede its target in the archive`() {
        val target = tmp.newFolder("target")
        extractor.extract(
            archive {
                symlink("java", "bin/java-real")
                dir("bin")
                file("bin/java-real", "launcher")
            },
            target,
        )
        assertEquals("launcher", File(target, "java").readText())
    }

    // ---- defect 2: merge instead of wipe ---------------------------------------------------

    @Test
    fun `extracting a parent-path component preserves a nested component`() {
        val root = tmp.newFolder("filesDir")
        // Simulate an already-installed jdk component nested under usr/lib.
        val jdkJava = File(root, "usr/lib/jvm/java-17-openjdk/bin/java")
        jdkJava.parentFile!!.mkdirs()
        jdkJava.writeText("installed-jdk")

        // Now (re)install jdk-native-libs into usr/lib.
        extractor.extract(
            archive {
                file("libz.so.1", "zlib")
                dir("engines")
                file("engines/ossl.so", "engine")
            },
            File(root, "usr/lib"),
        )

        assertEquals("installed-jdk", jdkJava.readText())
        assertEquals("zlib", File(root, "usr/lib/libz.so.1").readText())
        assertEquals("engine", File(root, "usr/lib/engines/ossl.so").readText())
        assertFalse(
            "staging dir must be cleaned up",
            File(root, "usr").listFiles()!!.any { it.name.contains("extract-tmp") },
        )
    }

    @Test
    fun `re-extracting overwrites files from the archive but keeps others`() {
        val target = tmp.newFolder("target")
        File(target, "keep.txt").writeText("keep-me")
        File(target, "replace.txt").writeText("old")

        extractor.extract(archive { file("replace.txt", "new") }, target)

        assertEquals("keep-me", File(target, "keep.txt").readText())
        assertEquals("new", File(target, "replace.txt").readText())
    }

    // ---- security ---------------------------------------------------------------------------

    @Test
    fun `path traversal entries are blocked`() {
        val root = tmp.newFolder("root")
        val target = File(root, "component")
        val evil = File(root, "evil.txt")
        try {
            extractor.extract(archive { file("../evil.txt", "pwned") }, target)
            throw AssertionError("expected SecurityException")
        } catch (expected: SecurityException) {
        }
        assertFalse("nothing may be written outside the target", evil.exists())
        assertFalse("nothing partial may reach the target", File(target, "evil.txt").exists())
    }

    @Test
    fun `symlink targets escaping the component are blocked`() {
        val target = tmp.newFolder("target")
        try {
            extractor.extract(
                archive {
                    dir("bin")
                    symlink("bin/bad", "../../outside")
                },
                target,
            )
            throw AssertionError("expected SecurityException")
        } catch (expected: SecurityException) {
        }
        assertFalse(File(target, "bin/bad").exists())
    }

    @Test
    fun `absolute symlink targets are blocked`() {
        val target = tmp.newFolder("target")
        try {
            extractor.extract(archive { symlink("bad", "/etc/passwd") }, target)
            throw AssertionError("expected SecurityException")
        } catch (expected: SecurityException) {
        }
    }

    @Test
    fun `failed extraction leaves existing files untouched`() {
        val target = tmp.newFolder("target")
        File(target, "replace.txt").writeText("old")
        try {
            extractor.extract(
                archive {
                    file("replace.txt", "new")
                    file("../evil.txt", "pwned") // rejected after the first entry was staged
                },
                target,
            )
            throw AssertionError("expected SecurityException")
        } catch (expected: SecurityException) {
        }
        assertEquals("staging must not leak into the target on failure", "old", File(target, "replace.txt").readText())
    }

    // ---- modes ------------------------------------------------------------------------------

    @Test
    fun `owner-execute bit is preserved`() {
        val target = tmp.newFolder("target")
        extractor.extract(
            archive {
                file("bin/java", "launcher", mode = "755".toInt(8))
                file("release", "JAVA_VERSION=17", mode = "644".toInt(8))
            },
            target,
        )
        assertTrue(File(target, "bin/java").canExecute())
        assertFalse(File(target, "release").canExecute())
    }

    @Test
    fun `existing symlink is replaced by a regular file without following it`() {
        val root = tmp.newFolder("root")
        val victim = File(root, "victim.txt").apply { writeText("victim") }
        val target = File(root, "component").apply { mkdirs() }
        Files.createSymbolicLink(File(target, "data.txt").toPath(), victim.toPath())

        extractor.extract(archive { file("data.txt", "fresh") }, target)

        assertEquals("fresh", File(target, "data.txt").readText())
        assertFalse(isSymlinkOnDisk(File(target, "data.txt")))
        assertEquals("the old link's target must be untouched", "victim", victim.readText())
    }
}
