package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedPlugin
import com.ahmadkharfan.androidstudiolite.data.gradle.model.VersionCatalog

object AgpVersionResolver {

    private val AGP_PLUGIN_IDS = setOf(
        "com.android.application",
        "com.android.library",
        "com.android.dynamic-feature",
        "com.android.test",
    )

    fun resolve(catalog: VersionCatalog?, modulePlugins: List<ParsedPlugin> = emptyList()): String? {
        modulePlugins.firstOrNull { it.id in AGP_PLUGIN_IDS && !it.version.isNullOrBlank() }
            ?.version?.let { return it }

        if (catalog != null) {
            catalog.plugins.firstOrNull { it.id in AGP_PLUGIN_IDS && !it.version.isNullOrBlank() }
                ?.version?.let { return it }

            catalog.libraries.firstOrNull {
                it.group == "com.android.tools.build" && it.name == "gradle" && !it.version.isNullOrBlank()
            }?.version?.let { return it }

            resolveFromVersions(catalog.versions)?.let { return it }
        }
        return null
    }

    private fun resolveFromVersions(versions: Map<String, String>): String? {
        fun normalize(key: String): String = key.lowercase().replace("-", "").replace("_", "").replace(".", "")

        val exact = setOf("agp", "androidgradleplugin", "androidgradlepluginversion")
        versions.entries.firstOrNull { normalize(it.key) in exact }?.let { return it.value.ifBlank { null } }
        versions.entries.firstOrNull { normalize(it.key).contains("androidgradleplugin") }
            ?.let { return it.value.ifBlank { null } }
        versions.entries.firstOrNull { normalize(it.key) == "android" }?.let { return it.value.ifBlank { null } }
        return null
    }
}
