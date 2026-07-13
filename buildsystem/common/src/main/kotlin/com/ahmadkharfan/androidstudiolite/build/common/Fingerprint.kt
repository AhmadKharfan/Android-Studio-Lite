package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File
import java.security.MessageDigest

/**
 * Content fingerprinting for incremental up-to-date checks. A fingerprint is a lowercase hex
 * SHA-256 digest; combining several fingerprints (files, string values) yields a stable digest that
 * changes iff any input changed. Deliberately content-based rather than timestamp-based so a
 * touched-but-unchanged file does not force a rebuild, and a restored older copy does.
 */
object Fingerprint {

    const val EMPTY: String = "0000000000000000000000000000000000000000000000000000000000000000"

    /** SHA-256 of raw bytes. */
    fun ofBytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** SHA-256 of a UTF-8 string. */
    fun ofString(value: String): String = ofBytes(value.toByteArray(Charsets.UTF_8))

    /**
     * SHA-256 of a file's contents. A missing file hashes to a fixed sentinel so its
     * appearance/disappearance is itself a change. Directories are hashed recursively over their
     * files' relative paths + contents (order-independent).
     */
    fun ofFile(file: File): String {
        if (!file.exists()) return ofString(" missing ")
        if (file.isDirectory) return ofDirectory(file)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /** Fingerprint of a directory tree: each file contributes `relativePath -> contentHash`. */
    fun ofDirectory(dir: File): String {
        if (!dir.exists()) return ofString(" missing ")
        val entries = dir.walkTopDown()
            .filter { it.isFile }
            .map { "${it.relativeTo(dir).invariantPath()}:${ofFile(it)}" }
            .sorted()
            .toList()
        return ofString(entries.joinToString("\n"))
    }

    /** Order-independent combination of many fingerprints/labels into one. */
    fun combine(parts: Iterable<String>): String =
        ofString(parts.sorted().joinToString("\n"))

    fun combine(vararg parts: String): String = combine(parts.asList())

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
