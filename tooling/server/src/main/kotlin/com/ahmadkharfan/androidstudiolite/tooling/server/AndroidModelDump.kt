package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.SourceSetDto
import java.io.File

/**
 * Extracts Android-specific structure (build types, product flavors, named source sets, app-vs-library
 * kind) that the generic Tooling-API models don't expose, without compiling against AGP.
 *
 * We can't ask the Tooling API for AGP's own models without depending on AGP's builder-model artifacts
 * (and coupling to a specific AGP version). Instead we hand Gradle a small init script that, during
 * configuration, reflects on each project's `android` extension and writes a JSON file per module into
 * a directory we own. After the model build completes we read those files back. On a plain JVM project
 * (the desktop harness) there is no `android` extension, so no files are written and this contributes
 * nothing — exactly the fallback behaviour we want until the on-device Android path is exercised.
 *
 * The init script is a build-time Groovy script (evaluated by Gradle, never shipped as bytecode); it is
 * written fresh here and shares no code with any GPL reference.
 */
class AndroidModelDump(private val outputDir: File) {

    data class AndroidModuleInfo(
        val isApplication: Boolean,
        val buildTypes: List<String>,
        val flavors: List<String>,
        val sourceSets: List<SourceSetDto>,
    )

    /** Writes the init script to [scriptFile] and returns it, ready to pass as `--init-script`. */
    fun writeInitScript(scriptFile: File): File {
        outputDir.mkdirs()
        // The output directory is baked into the script text because the init script runs in the Gradle
        // daemon JVM, which shares only the filesystem with this server process.
        val dir = outputDir.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
        scriptFile.writeText(
            """
            allprojects { proj ->
                proj.afterEvaluate {
                    def android = proj.extensions.findByName('android')
                    if (android == null) return
                    try {
                        def outDir = new File('$dir')
                        outDir.mkdirs()
                        def q = { s -> s == null ? 'null' : '"' + s.toString().replace('\\', '\\\\').replace('"', '\\"') + '"' }
                        def arr = { list -> '[' + list.collect { q(it) }.join(',') + ']' }
                        def isApp = proj.plugins.hasPlugin('com.android.application')
                        def buildTypes = android.buildTypes.collect { it.name }
                        def flavors = android.productFlavors.collect { it.name }
                        def sourceSets = android.sourceSets.collect { ss ->
                            def java = (ss.java?.srcDirs ?: []).collect { it.absolutePath }
                            def res = (ss.res?.srcDirs ?: []).collect { it.absolutePath }
                            def assets = (ss.assets?.srcDirs ?: []).collect { it.absolutePath }
                            def manifest = null
                            try { manifest = ss.manifest?.srcFile?.absolutePath } catch (ignored) {}
                            '{"name":' + q(ss.name) + ',"javaDirs":' + arr(java) +
                                ',"resDirs":' + arr(res) + ',"assetsDirs":' + arr(assets) +
                                ',"manifest":' + q(manifest) + '}'
                        }
                        def json = '{"path":' + q(proj.path) + ',"isApplication":' + isApp +
                            ',"buildTypes":' + arr(buildTypes) + ',"flavors":' + arr(flavors) +
                            ',"sourceSets":[' + sourceSets.join(',') + ']}'
                        new File(outDir, proj.path.replace(':', '_') + '.json').text = json
                    } catch (Throwable t) {
                        // Never let model extraction fail the sync; the generic model still applies.
                        System.err.println('asl-tooling: android model dump failed for ' + proj.path + ': ' + t)
                    }
                }
            }
            """.trimIndent(),
        )
        return scriptFile
    }

    /** Reads every dumped module file, keyed by Gradle project path (":app"). */
    fun readAll(): Map<String, AndroidModuleInfo> {
        val files = outputDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyMap()
        val result = LinkedHashMap<String, AndroidModuleInfo>()
        for (file in files) {
            val obj = runCatching { JsonValue.parse(file.readText()) as? JsonValue.Obj }.getOrNull() ?: continue
            val path = obj.string("path") ?: continue
            result[path] = AndroidModuleInfo(
                isApplication = obj.bool("isApplication") ?: false,
                buildTypes = strings(obj.array("buildTypes")),
                flavors = strings(obj.array("flavors")),
                sourceSets = obj.array("sourceSets")?.items?.mapNotNull { item ->
                    (item as? JsonValue.Obj)?.let { ss ->
                        SourceSetDto(
                            name = ss.string("name").orEmpty(),
                            javaDirs = strings(ss.array("javaDirs")),
                            resDirs = strings(ss.array("resDirs")),
                            assetsDirs = strings(ss.array("assetsDirs")),
                            manifestFile = ss.string("manifest"),
                        )
                    }
                }.orEmpty(),
            )
        }
        return result
    }

    private fun strings(arr: JsonValue.Arr?): List<String> =
        arr?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty()
}
