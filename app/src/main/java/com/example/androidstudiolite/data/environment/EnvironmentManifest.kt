package com.example.androidstudiolite.data.environment

import org.json.JSONObject

/**
 * One entry in the hosted environment manifest — a single archive for a single ABI. `targetPath` is
 * relative to [com.example.androidstudiolite.core.environment.IdeEnvironmentPaths.root]: for example
 * `"usr/lib/jvm/java-17-openjdk"` for the JDK, or `"home/android-sdk/build-tools/34.0.0"` for a
 * build-tools revision. The archive's contents are extracted so that its root lands at that path.
 */
data class EnvironmentComponentSpec(
    val id: String,
    val displayName: String,
    val version: String,
    val targetPath: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

data class EnvironmentManifest(val components: List<EnvironmentComponentSpec>) {
    companion object {
        /**
         * Parses the manifest JSON:
         * ```json
         * { "components": [ { "id": "jdk", "displayName": "OpenJDK 17", "version": "17.0.11",
         *     "targetPath": "usr/lib/jvm/java-17-openjdk",
         *     "abis": { "arm64-v8a": { "url": "...", "sha256": "...", "sizeBytes": 123 }, "armeabi-v7a": {...} } } ] }
         * ```
         * Only the entry matching [abi] is kept for each component; a component with no build for
         * this device's ABI is dropped (surfaced by the repository as an unsupported-device state).
         */
        fun parse(json: String, abi: String): EnvironmentManifest {
            val root = JSONObject(json)
            val componentsJson = root.getJSONArray("components")
            val specs = buildList {
                for (i in 0 until componentsJson.length()) {
                    val c = componentsJson.getJSONObject(i)
                    val abis = c.getJSONObject("abis")
                    if (!abis.has(abi)) continue
                    val abiEntry = abis.getJSONObject(abi)
                    add(
                        EnvironmentComponentSpec(
                            id = c.getString("id"),
                            displayName = c.getString("displayName"),
                            version = c.getString("version"),
                            targetPath = c.getString("targetPath"),
                            downloadUrl = abiEntry.getString("url"),
                            sha256 = abiEntry.getString("sha256"),
                            sizeBytes = abiEntry.getLong("sizeBytes"),
                        ),
                    )
                }
            }
            return EnvironmentManifest(specs)
        }
    }
}
