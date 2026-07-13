package com.ahmadkharfan.androidstudiolite.build.engine.maven

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches raw bytes for a repository-relative path (e.g. `androidx/core/core/1.13.0/core-1.13.0.pom`).
 * Abstracted so resolution is unit-testable against a local fixture directory with no network.
 */
fun interface ArtifactFetcher {
    /** @return the bytes, or null if the path is absent (404). Throws [IOException] on transport error. */
    fun fetch(relativePath: String): ByteArray?
}

/** Serves artifacts from a local directory laid out like a Maven repo — used by tests and the cache. */
class DirectoryFetcher(private val root: File) : ArtifactFetcher {
    override fun fetch(relativePath: String): ByteArray? {
        val f = File(root, relativePath)
        return if (f.isFile) f.readBytes() else null
    }
}

/** Serves artifacts from an HTTP(S) Maven repository (Maven Central / Google Maven). */
class HttpFetcher(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
) : ArtifactFetcher {
    override fun fetch(relativePath: String): ByteArray? {
        val url = URL("${baseUrl.trimEnd('/')}/$relativePath")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = "GET"
        try {
            return when (val code = conn.responseCode) {
                in 200..299 -> conn.inputStream.use { it.readBytes() }
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN -> null
                else -> throw IOException("HTTP $code fetching $url")
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * The set of repositories a resolution consults, with a persistent on-disk cache for offline reuse.
 * A resolved artifact is looked up cache-first, then each remote in order; the first hit is copied
 * into the cache. Negative results (404s) are not cached so a later publish is still found.
 */
class MavenRepositories(
    private val remotes: List<ArtifactFetcher>,
    private val cacheDir: File,
) {
    private val cache = DirectoryFetcher(cacheDir)

    /** Default: Google Maven then Maven Central, mirroring a standard Android project's repositories. */
    constructor(cacheDir: File) : this(
        remotes = listOf(
            HttpFetcher("https://dl.google.com/dl/android/maven2"),
            HttpFetcher("https://repo1.maven.org/maven2"),
        ),
        cacheDir = cacheDir,
    )

    /** Bytes for [relativePath], or null if no repository has it. Populates the cache on a remote hit. */
    fun get(relativePath: String): ByteArray? {
        cache.fetch(relativePath)?.let { return it }
        for (remote in remotes) {
            val bytes = try {
                remote.fetch(relativePath)
            } catch (e: IOException) {
                continue // try the next mirror; only fail if all fail
            }
            if (bytes != null) {
                writeCache(relativePath, bytes)
                return bytes
            }
        }
        return null
    }

    /** The cached file for [relativePath], materialising it from a remote if necessary. */
    fun getFile(relativePath: String): File? {
        val cached = File(cacheDir, relativePath)
        if (cached.isFile) return cached
        val bytes = get(relativePath) ?: return null
        return File(cacheDir, relativePath).takeIf { it.isFile } ?: run {
            writeCache(relativePath, bytes); File(cacheDir, relativePath)
        }
    }

    private fun writeCache(relativePath: String, bytes: ByteArray) {
        val target = File(cacheDir, relativePath)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
