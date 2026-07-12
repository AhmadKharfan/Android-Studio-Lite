package com.ahmadkharfan.androidstudiolite.data.environment

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Extracts a `.tar.xz` component archive so its contents land directly under a target directory.
 *
 * Semantics the toolchain hosting pipeline relies on (docs/build-run/11-toolchain-hosting-runbook.md):
 * - **Merge, don't wipe.** The target directory is never deleted, so a component nested inside
 *   another component's targetPath survives — e.g. `jdk` (usr/lib/jvm/java-17-openjdk) is not
 *   destroyed when `jdk-native-libs` (usr/lib) is re-extracted. Files present in the archive
 *   overwrite files on disk; files not in the archive are left alone.
 * - **Staging then move.** Entries are fully extracted and validated in a sibling staging
 *   directory first, then moved into place, so a truncated/malicious archive cannot leave a
 *   half-overwritten component behind.
 * - **Symlinks are real symlinks** (created via [createSymlink]), not empty files. Targets must
 *   be relative and resolve inside the target directory; absolute or escaping targets are
 *   rejected, as are entry names that resolve outside it (zip-slip).
 * - The owner-execute bit is preserved: these archives hold real JDK/toolchain binaries.
 *
 * [createSymlink] is injected because `android.system.Os.symlink` is unavailable in JVM unit
 * tests and `java.nio.file` is unavailable below API 26 (minSdk is 24): production passes
 * `Os.symlink`, tests pass `Files.createSymbolicLink`.
 */
class TarXzExtractor(private val createSymlink: (target: String, link: File) -> Unit) {

    private enum class Kind { DIR, FILE, SYMLINK }
    private class Entry(val relPath: String, val kind: Kind)

    fun extract(archive: File, targetDir: File) {
        targetDir.mkdirs()
        val canonicalTarget = targetDir.canonicalFile
        val staging = File(
            canonicalTarget.parentFile ?: throw IOException("Target directory $targetDir has no parent"),
            "${canonicalTarget.name}.extract-tmp",
        )
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create staging directory $staging")
        try {
            val entries = unpackToStaging(archive, staging)
            merge(entries, staging, canonicalTarget)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun unpackToStaging(archive: File, staging: File): List<Entry> {
        val stagingRoot = staging.canonicalFile
        val entries = mutableListOf<Entry>()
        XZCompressorInputStream(BufferedInputStream(archive.inputStream())).use { xz ->
            TarArchiveInputStream(xz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val outFile = File(stagingRoot, entry.name).canonicalFile
                    // Zip-slip guard: an archive entry must never resolve outside the staging root.
                    if (outFile != stagingRoot && !outFile.path.startsWith(stagingRoot.path + File.separator)) {
                        throw SecurityException("Blocked path traversal in archive entry: ${entry.name}")
                    }
                    if (outFile == stagingRoot) {
                        entry = tar.nextEntry
                        continue
                    }
                    val relPath = outFile.path.removePrefix(stagingRoot.path + File.separator)
                    when {
                        entry.isDirectory -> {
                            outFile.mkdirs()
                            entries += Entry(relPath, Kind.DIR)
                        }
                        entry.isSymbolicLink -> {
                            val target = entry.linkName
                            requireSymlinkTargetInside(relPath, target)
                            outFile.parentFile?.mkdirs()
                            outFile.delete()
                            try {
                                createSymlink(target, outFile)
                            } catch (e: Exception) {
                                throw IOException("Failed to create symlink $relPath -> $target", e)
                            }
                            entries += Entry(relPath, Kind.SYMLINK)
                        }
                        entry.isLink -> throw IOException("Hard-link entries are not supported: ${entry.name}")
                        else -> {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                            // Preserve the executable bit: this archive holds real JDK/toolchain binaries.
                            if (entry.mode and OWNER_EXECUTE_BIT != 0) outFile.setExecutable(true)
                            entries += Entry(relPath, Kind.FILE)
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
        return entries
    }

    /**
     * A symlink target must be relative and, resolved lexically from the link's directory, must
     * stay inside the extraction root. Because every link in the archive satisfies this, chains of
     * links also cannot escape.
     */
    private fun requireSymlinkTargetInside(linkRelPath: String, target: String) {
        if (target.isEmpty() || File(target).isAbsolute) {
            throw SecurityException("Blocked absolute symlink target: $linkRelPath -> $target")
        }
        val depth = ArrayDeque<String>()
        linkRelPath.split(File.separatorChar).dropLast(1).forEach { depth.addLast(it) }
        for (segment in target.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> depth.removeLastOrNull()
                    ?: throw SecurityException("Blocked symlink escaping the component: $linkRelPath -> $target")
                else -> depth.addLast(segment)
            }
        }
    }

    private fun merge(entries: List<Entry>, staging: File, targetRoot: File) {
        for (e in entries) {
            val dest = File(targetRoot, e.relPath)
            when (e.kind) {
                Kind.DIR -> {
                    if (isSymlink(dest) || dest.isFile) dest.delete()
                    dest.mkdirs()
                }
                Kind.FILE, Kind.SYMLINK -> {
                    dest.parentFile?.mkdirs()
                    when {
                        isSymlink(dest) -> dest.delete() // never follow: delete the link itself
                        dest.isDirectory -> dest.deleteRecursively()
                        else -> dest.delete()
                    }
                    val src = File(staging, e.relPath)
                    if (!src.renameTo(dest)) {
                        throw IOException("Failed to move ${e.relPath} into ${targetRoot.name}")
                    }
                }
            }
        }
    }

    /** Pre-NIO symlink detection (minSdk 24): canonicalize the parent, then compare the leaf. */
    private fun isSymlink(file: File): Boolean {
        val resolvedLeaf = file.absoluteFile.parentFile?.canonicalFile?.let { File(it, file.name) }
            ?: file.absoluteFile
        return runCatching { resolvedLeaf.canonicalFile != resolvedLeaf.absoluteFile }.getOrDefault(false)
    }

    private companion object {
        /** Unix `st_mode` owner-execute bit (octal 0100). */
        const val OWNER_EXECUTE_BIT = 0b001_000_000
    }
}
