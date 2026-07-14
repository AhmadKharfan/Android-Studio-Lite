package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.ahmadkharfan.androidstudiolite.build.common.Fingerprint
import java.io.File

/**
 * Content-addressed cache of per-library dex archives. Dexing dependency jars dominates a clean
 * build's cost, but a given `jar@minApi` always dexes to the same output — so we key the cached dex
 * archive on the jar's content hash (plus min-API and a tool tag). On a rebuild, unchanged
 * dependencies are served straight from cache and only the app's own classes are re-dexed and merged.
 */
class DexArchiveCache(
    private val cacheDir: File,
    private val dexTool: DexTool,
    /** Bumped when the dexer changes so stale entries are ignored rather than mis-served. */
    private val toolTag: String = "d8",
) {
    /** Dex [jar] against [androidJar], returning a cached dex-archive zip. Idempotent per content. */
    fun dexLibrary(jar: File, minApiLevel: Int, androidJar: File): File {
        val key = Fingerprint.combine(
            "jar:${Fingerprint.ofFile(jar)}",
            "minApi:$minApiLevel",
            "tool:$toolTag",
        )
        val archive = File(cacheDir, "$key.dex.zip")
        if (archive.isFile) return archive

        cacheDir.mkdirs()
        val tmp = File(cacheDir, "$key.tmp.zip")
        if (tmp.exists()) tmp.delete()
        dexTool.dex(
            DexRequest(
                programFiles = listOf(jar),
                libraryFiles = listOf(androidJar),
                minApiLevel = minApiLevel,
                output = tmp,
            ),
        )
        if (!tmp.renameTo(archive)) {
            tmp.copyTo(archive, overwrite = true)
            tmp.delete()
        }
        return archive
    }

    /** Dex every jar in [jars] (cached), returning their dex archives in the same order. */
    fun dexLibraries(jars: List<File>, minApiLevel: Int, androidJar: File): List<File> =
        jars.map { dexLibrary(it, minApiLevel, androidJar) }
}
