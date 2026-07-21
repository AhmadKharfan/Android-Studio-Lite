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

class ProjectPackager(
    private val excludedDirs: Set<String> = DEFAULT_EXCLUDED_DIRS,
) {

    suspend fun packageProjectCached(projectRoot: File, cacheDir: File): File = withContext(Dispatchers.IO) {
        require(projectRoot.isDirectory) { "Not a directory: $projectRoot" }
        cacheDir.mkdirs()
        val zip = File(cacheDir, CACHED_ZIP_NAME)
        val stamp = File(cacheDir, CACHED_STAMP_NAME)
        val current = fingerprint(projectRoot)

        val cached = zip.isFile && runCatching { stamp.readText() }.getOrNull() == current
        if (cached) return@withContext zip

        packageProject(projectRoot, zip)


        runCatching { stamp.writeText(current) }
        zip
    }

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

    fun projectKey(projectRoot: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(projectRoot.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    private fun fingerprint(projectRoot: File): String {


        val sb = StringBuilder(projectRoot.absolutePath).append('\n')
        fun walk(dir: File) {
            val children = dir.listFiles() ?: return
            for (child in children.sortedBy { it.name }) {
                if (child.isDirectory) {
                    if (isExcludedDir(child)) continue
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
                if (isExcludedDir(child)) continue
                if (child.listFiles().isNullOrEmpty()) {

                    zip.putNextEntry(ZipEntry(relativePath(root, child) + "/"))
                    zip.closeEntry()
                } else {
                    zipDirectory(root, child, zip)
                }
            } else if (child.isFile) {
                val entry = ZipEntry(relativePath(root, child))


                if (child.canExecute() || child.name == "gradlew") {
                    setUnixMode(entry, UNIX_FILE_EXECUTABLE)
                }
                zip.putNextEntry(entry)
                child.inputStream().use { it.copyTo(zip, bufferSize = BUFFER_SIZE) }
                zip.closeEntry()
            }
        }
    }

    private fun isExcludedDir(dir: File): Boolean {
        if (dir.name !in excludedDirs) return false
        if (dir.name != "build") return true // .gradle/.idea/.git are always tool/output dirs
        return isGeneratedBuildDir(dir)
    }

    private fun isGeneratedBuildDir(dir: File): Boolean {
        val parent = dir.parentFile ?: return false
        val parentIsGradleProject =
            File(parent, "build.gradle.kts").isFile || File(parent, "build.gradle").isFile ||
                File(parent, "settings.gradle.kts").isFile || File(parent, "settings.gradle").isFile
        return parentIsGradleProject && !looksLikeGradleModule(dir)
    }

    private fun looksLikeGradleModule(dir: File): Boolean =
        File(dir, "build.gradle.kts").isFile ||
            File(dir, "build.gradle").isFile ||
            File(dir, "settings.gradle.kts").isFile ||
            File(dir, "settings.gradle").isFile ||
            File(dir, "src").isDirectory

    private fun relativePath(root: File, file: File): String =
        file.relativeTo(root).path.replace(File.separatorChar, '/')

    private fun setUnixMode(entry: ZipEntry, mode: Int) {
        val attrs = (mode.toLong() and 0xffffL) shl 16
        try {
            val method = ZipEntry::class.java.getMethod(
                "setExternalAttributes",
                Long::class.javaPrimitiveType,
            )
            method.invoke(entry, attrs)
            return
        } catch (_: Throwable) {

        }
        for (name in listOf("externalAttrs", "externalAttributes", "extraAttributes")) {
            try {
                val field = ZipEntry::class.java.getDeclaredField(name)
                field.isAccessible = true
                when (field.type) {
                    java.lang.Long.TYPE, java.lang.Long::class.java -> field.setLong(entry, attrs)
                    java.lang.Integer.TYPE, java.lang.Integer::class.java -> field.setInt(entry, attrs.toInt())
                    else -> continue
                }
                return
            } catch (_: Throwable) {

            }
        }
    }

    companion object {
        val DEFAULT_EXCLUDED_DIRS: Set<String> = setOf("build", ".gradle", ".idea", ".git")
        private const val BUFFER_SIZE = 64 * 1024
        private const val UNIX_FILE_EXECUTABLE = 0b1000000111101101

        private const val CACHED_ZIP_NAME = "cached-source.zip"
        private const val CACHED_STAMP_NAME = "cached-source.fingerprint"
    }
}
