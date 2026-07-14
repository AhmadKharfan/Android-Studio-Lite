package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.BufferedOutputStream
import java.io.File
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
    }
}
