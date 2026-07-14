package com.ahmadkharfan.androidstudiolite.build.engine.tools

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Assembles an unsigned APK from the aapt2-linked resource APK (binary manifest + `resources.arsc` +
 * `res/`) plus the dexed code and any assets/native libs. Performs zipalign-style alignment inline —
 * `STORED` entries are padded to a 4-byte boundary and native `.so`s to the 4 KiB page boundary — so
 * the result is directly signable and mmap-friendly, with no separate `zipalign` binary.
 */
class ApkPackager {

    fun buildUnsignedApk(
        baseApk: File?,
        dexFiles: List<File>,
        output: File,
        assetsDir: File? = null,
        nativeLibsDir: File? = null,
        /** Used only when [baseApk] is null (no resources): a raw entry named AndroidManifest.xml. */
        rawManifest: File? = null,
    ) {
        output.parentFile?.mkdirs()
        val counting = CountingOutputStream(output.outputStream().buffered())
        ZipOutputStream(counting).use { zip ->
            // 1. Resources + binary manifest from the aapt2 output, preserving compression/alignment.
            if (baseApk != null) {
                ZipFile(baseApk).use { base ->
                    val entries = base.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        base.getInputStream(entry).use { input ->
                            writeEntry(zip, counting, entry.name, input.readBytes(), storedForName(entry.name))
                        }
                    }
                }
            } else if (rawManifest != null) {
                writeEntry(zip, counting, "AndroidManifest.xml", rawManifest.readBytes(), stored = false)
            }

            // 2. Dex code.
            dexFiles.forEachIndexed { index, dex ->
                val name = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
                writeEntry(zip, counting, name, dex.readBytes(), stored = false)
            }

            // 3. Assets and native libraries.
            assetsDir?.takeIf { it.isDirectory }?.let { dir ->
                addTree(zip, counting, dir, "assets/")
            }
            nativeLibsDir?.takeIf { it.isDirectory }?.let { dir ->
                addTree(zip, counting, dir, "lib/")
            }
        }
    }

    private fun addTree(zip: ZipOutputStream, counting: CountingOutputStream, dir: File, prefix: String) {
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val name = prefix + file.relativeTo(dir).path.replace(File.separatorChar, '/')
            writeEntry(zip, counting, name, file.readBytes(), stored = storedForName(name))
        }
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        counting: CountingOutputStream,
        name: String,
        data: ByteArray,
        stored: Boolean,
    ) {
        val entry = ZipEntry(name)
        if (stored) {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = CRC32().apply { update(data) }.value
            alignStored(entry, counting.count, name)
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    /**
     * Pad the entry's extra field so that its *data* starts on the required boundary. The local file
     * header is 30 bytes + name + extra; [bytesWritten] is the offset where the header begins.
     */
    private fun alignStored(entry: ZipEntry, bytesWritten: Long, name: String) {
        val alignment = if (name.endsWith(".so")) 4096 else 4
        val headerEnd = bytesWritten + LOCAL_HEADER_FIXED + entry.name.toByteArray(Charsets.UTF_8).size
        val padding = ((alignment - (headerEnd % alignment)) % alignment).toInt()
        if (padding > 0) entry.extra = ByteArray(padding)
    }

    private fun storedForName(name: String): Boolean =
        name == "resources.arsc" || name.endsWith(".so")

    /** Tracks how many bytes have been written so entry alignment can be computed. */
    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count: Long = 0
            private set

        override fun write(b: Int) {
            super.out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            super.out.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val LOCAL_HEADER_FIXED = 30 // zip local file header fixed portion, per APPNOTE
    }
}
