package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Zips a project directory into a single archive for upload to the build server, excluding
 * build/tooling scratch directories that would only bloat the upload (they're regenerated
 * server-side). Entries use project-relative POSIX paths, matching what the worker unzips into
 * `/workspace` and what the wire protocol expects for `BuildEvent`/`ProjectModel` paths.
 *
 * Streams straight to disk (buffered) so large projects don't have to fit in memory; the resulting
 * file is then streamed to the presigned upload URL by [RemoteClient.uploadSource].
 */
class ProjectPackager(
    /** Directory names pruned entirely, at any depth. */
    private val excludedDirs: Set<String> = DEFAULT_EXCLUDED_DIRS,
) {

    /**
     * Zips [projectRoot] into a cached archive under [cacheDir], reusing the previous archive when
     * the source tree has not changed since it was written. Returns the zip, which belongs to the
     * cache — the caller must NOT delete it.
     *
     * Every Run used to re-zip the whole tree, compressing and rewriting every byte even when the
     * user changed nothing (the common case: hit Run, build fails, hit Run again). [fingerprint]
     * only stats the tree, so an unchanged project skips that work entirely.
     *
     * The fingerprint is deliberately not a content hash: reading every file to hash it costs about
     * what zipping costs, which would defeat the purpose. Sizes + mtimes catch every edit the IDE
     * itself makes. The gap is an edit that preserves both — for a local scratch dir written by this
     * app, that means a same-size write within the filesystem's mtime resolution. A stale zip would
     * cost a build of stale source, so on any doubt (unreadable cache, unparseable state) this falls
     * back to re-zipping rather than trusting the cache.
     */
    suspend fun packageProjectCached(projectRoot: File, cacheDir: File): File = withContext(Dispatchers.IO) {
        require(projectRoot.isDirectory) { "Not a directory: $projectRoot" }
        cacheDir.mkdirs()
        val zip = File(cacheDir, CACHED_ZIP_NAME)
        val stamp = File(cacheDir, CACHED_STAMP_NAME)
        val current = fingerprint(projectRoot)

        val cached = zip.isFile && runCatching { stamp.readText() }.getOrNull() == current
        if (cached) return@withContext zip

        packageProject(projectRoot, zip)
        // Written only after the zip is complete, so a crash mid-zip leaves a stale-but-mismatched
        // stamp and the next build re-zips instead of uploading a truncated archive.
        runCatching { stamp.writeText(current) }
        zip
    }

    /**
     * SHA-256 (hex) of [zip]'s bytes, for the server's source dedup.
     *
     * This is a real content hash, not the [fingerprint] below: it is what the server keys the
     * stored object on, so a collision with a DIFFERENT tree would build the wrong source. It is
     * also cheap next to what it saves — reading a few MB off local disk to avoid uploading those
     * same MB over mobile data.
     *
     * Hashing the cached zip FILE (rather than re-deriving it from the tree) is what makes it
     * stable: [packageProjectCached] returns the very same file bytes for an unchanged project, so
     * an unchanged project hashes identically, while any re-zip produces a new hash.
     */
    suspend fun hashZip(zip: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * A stable id for [projectRoot] that survives edits — the opposite requirement to [hashZip].
     *
     * It names the project's configuration-cache slot on the server, so it must NOT change when the
     * code does (an edit is exactly when reusing cached configuration pays off; Gradle invalidates
     * the entry itself when the build logic changes). The absolute path is that stable identity, and
     * it is hashed rather than sent so the wire never carries the user's on-device paths. The server
     * additionally namespaces it per device.
     */
    fun projectKey(projectRoot: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(projectRoot.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    /**
     * A cheap identity for the source tree: every included file's relative path, size and mtime.
     * Same traversal and exclusions as [packageProject], so anything that would land in the zip is
     * covered and anything pruned from it is ignored.
     */
    private fun fingerprint(projectRoot: File): String {
        // Rooted at the project path: entries below are project-RELATIVE, so without this two
        // different projects that happen to share a shape could match each other's stamp and one
        // would build from the other's source.
        val sb = StringBuilder(projectRoot.absolutePath).append('\n')
        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children.sortedBy { it.name }) {
                if (child.isDirectory) {
                    if (child.name in excludedDirs) continue
                    walk(child)
                } else if (child.isFile) {
                    sb.append(relativePath(projectRoot, child))
                        .append(':').append(child.length())
                        .append(':').append(child.lastModified())
                        .append('\n')
                }
            }
        }
        walk(projectRoot)
        return sb.toString()
    }

    /**
     * Zips [projectRoot] into [dest] (created/overwritten). Returns [dest]. Cancels cooperatively —
     * a cancelled coroutine leaves a partial file that the caller should delete.
     */
    suspend fun packageProject(projectRoot: File, dest: File): File = withContext(Dispatchers.IO) {
        require(projectRoot.isDirectory) { "Not a directory: $projectRoot" }
        dest.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(dest.outputStream())).use { zip ->
            zipDirectory(projectRoot, projectRoot, zip)
        }
        dest
    }

    private suspend fun zipDirectory(root: File, dir: File, zip: ZipOutputStream) {
        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            coroutineContext.ensureActive()
            if (child.isDirectory) {
                if (child.name in excludedDirs) continue
                if (child.listFiles().isNullOrEmpty()) {
                    // Preserve empty directories with a trailing-slash entry.
                    zip.putNextEntry(ZipEntry(relativePath(root, child) + "/"))
                    zip.closeEntry()
                } else {
                    zipDirectory(root, child, zip)
                }
            } else if (child.isFile) {
                zip.putNextEntry(ZipEntry(relativePath(root, child)))
                child.inputStream().use { it.copyTo(zip, bufferSize = BUFFER_SIZE) }
                zip.closeEntry()
            }
        }
    }

    private fun relativePath(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    companion object {
        /** Regenerated server-side or IDE-local, so never worth uploading. */
        val DEFAULT_EXCLUDED_DIRS: Set<String> = setOf("build", ".gradle", ".idea", ".git")
        private const val BUFFER_SIZE = 64 * 1024

        /**
         * One cache slot, not one per project: consecutive Runs of the SAME project are the case
         * worth optimising, and a per-project cache would grow without bound in cacheDir. Switching
         * projects just re-zips once.
         */
        private const val CACHED_ZIP_NAME = "cached-source.zip"
        private const val CACHED_STAMP_NAME = "cached-source.fingerprint"
    }
}
