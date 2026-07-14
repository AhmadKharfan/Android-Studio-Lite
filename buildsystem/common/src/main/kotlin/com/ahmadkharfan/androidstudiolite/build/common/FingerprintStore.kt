package com.ahmadkharfan.androidstudiolite.build.common

import java.io.File

/**
 * Persists one line per task — `taskPath=fingerprint` — recording the combined input/output
 * fingerprint from the last successful run. Used by [TaskExecutor] to decide whether a task is
 * up-to-date. Deliberately a tiny flat properties-style file (no JSON dependency) so it is trivial
 * to read, diff, and delete.
 */
class FingerprintStore private constructor(
    private val file: File,
    private val entries: MutableMap<String, String>,
) {
    fun get(taskPath: String): String? = entries[taskPath]

    fun put(taskPath: String, fingerprint: String) {
        entries[taskPath] = fingerprint
    }

    fun remove(taskPath: String) {
        entries.remove(taskPath)
    }

    /** Atomically write the store to disk. */
    fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.bufferedWriter().use { out ->
            for ((key, value) in entries.toSortedMap()) {
                out.write(encode(key))
                out.write("=")
                out.write(value)
                out.write("\n")
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        fun load(file: File): FingerprintStore {
            val entries = LinkedHashMap<String, String>()
            if (file.isFile) {
                file.forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                    val eq = line.indexOf('=')
                    if (eq > 0) entries[decode(line.substring(0, eq))] = line.substring(eq + 1)
                }
            }
            return FingerprintStore(file, entries)
        }

        // Keys are task paths (":app:mergeDebugResources") — no '=' or newline, but escape defensively.
        private fun encode(key: String): String =
            key.replace("\\", "\\\\").replace("=", "\\=").replace("\n", "\\n")

        private fun decode(key: String): String =
            key.replace("\\n", "\n").replace("\\=", "=").replace("\\\\", "\\")
    }
}
