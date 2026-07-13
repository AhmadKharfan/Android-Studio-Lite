package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {

    @Test fun `same content hashes equal`() {
        assertEquals(Fingerprint.ofString("hello"), Fingerprint.ofString("hello"))
    }

    @Test fun `different content hashes differ`() {
        assertNotEquals(Fingerprint.ofString("a"), Fingerprint.ofString("b"))
    }

    @Test fun `missing file has a stable sentinel distinct from empty content`() {
        val dir = Files.createTempDirectory("fp").toFile()
        val missing = File(dir, "nope.txt")
        val empty = File(dir, "empty.txt").apply { writeText("") }
        assertEquals(Fingerprint.ofFile(missing), Fingerprint.ofFile(File(dir, "still-nope.txt")))
        assertNotEquals(Fingerprint.ofFile(missing), Fingerprint.ofFile(empty))
    }

    @Test fun `file fingerprint tracks content, not timestamp`() {
        val dir = Files.createTempDirectory("fp").toFile()
        val f = File(dir, "a.txt").apply { writeText("one") }
        val h1 = Fingerprint.ofFile(f)
        f.writeText("one") // same content, new mtime
        assertEquals(h1, Fingerprint.ofFile(f))
        f.writeText("two")
        assertNotEquals(h1, Fingerprint.ofFile(f))
    }

    @Test fun `directory fingerprint is order-independent and content-sensitive`() {
        val dir = Files.createTempDirectory("fp").toFile()
        File(dir, "a.txt").writeText("A")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.txt").writeText("B")
        val h1 = Fingerprint.ofDirectory(dir)
        File(dir, "sub/b.txt").writeText("B2")
        assertNotEquals(h1, Fingerprint.ofDirectory(dir))
    }

    @Test fun `combine is order-independent`() {
        assertEquals(Fingerprint.combine("x", "y", "z"), Fingerprint.combine("z", "y", "x"))
    }
}
