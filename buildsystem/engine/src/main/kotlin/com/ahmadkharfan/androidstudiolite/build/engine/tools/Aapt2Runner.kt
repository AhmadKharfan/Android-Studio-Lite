package com.ahmadkharfan.androidstudiolite.build.engine.tools

import java.io.File

/** Result of an aapt2 invocation: exit code plus merged stdout/stderr. */
data class Aapt2Result(val exitCode: Int, val output: String) {
    val success: Boolean get() = exitCode == 0
}

/** A single `aapt2 link` invocation. */
data class Aapt2LinkRequest(
    /** Compiled-resource inputs (the `.flat` zips produced by [Aapt2Tool.compile]). */
    val compiledResources: List<File>,
    val manifest: File,
    /** `android.jar` — passed as `-I`. */
    val androidJar: File,
    /** Where the linked resource APK (arsc + manifest + res) is written. */
    val outputApk: File,
    /** Directory into which `R.java` is generated. */
    val javaOutputDir: File,
    val minSdk: Int,
    val targetSdk: Int,
    /** Extra `-I` inputs, e.g. dependency resource APKs. */
    val extraInputApks: List<File> = emptyList(),
)

/** Wraps the aapt2 resource compiler/linker. Abstracted so the pipeline is testable with a fake. */
interface Aapt2Tool {
    /** Compile a res directory into [outputZip] (a zip of `.flat` files). */
    fun compile(resDir: File, outputZip: File): Aapt2Result

    /** Link compiled resources + manifest into a resource APK and generate `R.java`. */
    fun link(request: Aapt2LinkRequest): Aapt2Result
}

/**
 * Execs the aapt2 native binary shipped in the play APK's `jniLibs` (run from `nativeLibraryDir`,
 * which is exec-safe even under a high targetSdk). This is the *only* downloaded/bundled executable
 * the play flavor uses; every other tool runs in-process on ART.
 */
class Aapt2Runner(private val aapt2Binary: File) : Aapt2Tool {

    override fun compile(resDir: File, outputZip: File): Aapt2Result {
        outputZip.parentFile?.mkdirs()
        return exec(listOf("compile", "--dir", resDir.absolutePath, "-o", outputZip.absolutePath))
    }

    override fun link(request: Aapt2LinkRequest): Aapt2Result {
        request.outputApk.parentFile?.mkdirs()
        request.javaOutputDir.mkdirs()
        val args = buildList {
            add("link")
            add("-o"); add(request.outputApk.absolutePath)
            add("-I"); add(request.androidJar.absolutePath)
            request.extraInputApks.forEach { add("-I"); add(it.absolutePath) }
            add("--manifest"); add(request.manifest.absolutePath)
            add("--java"); add(request.javaOutputDir.absolutePath)
            add("--min-sdk-version"); add(request.minSdk.toString())
            add("--target-sdk-version"); add(request.targetSdk.toString())
            add("--auto-add-overlay")
            request.compiledResources.forEach { add("-R"); add(it.absolutePath) }
        }
        return exec(args)
    }

    private fun exec(args: List<String>): Aapt2Result {
        val command = listOf(aapt2Binary.absolutePath) + args
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return Aapt2Result(code, output)
    }
}
