package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import java.io.File

/** Inputs for one D8 invocation. */
data class DexRequest(
    /** `.class` directories, `.jar`s, or `.dex`/dex-archive zips to convert/merge. */
    val programFiles: List<File>,
    /** `android.jar` — required for correct desugaring of library references. */
    val libraryFiles: List<File>,
    val minApiLevel: Int,
    /** Output directory (DexIndexed → `classes.dex`, `classes2.dex`, …) or a `.zip`/`.jar` file. */
    val output: File,
    val debuggable: Boolean = true,
)

/** class/jar/dex → dex conversion & merging. Abstracted so the pipeline is testable with a fake. */
fun interface DexTool {
    fun dex(request: DexRequest)
}

/**
 * D8-backed [DexTool] (AOSP, Apache-2.0), run in-process. Converts and merges to DEX without shelling
 * out. Used both to dex the app's own classes and to merge per-library dex archives (see
 * [DexArchiveCache]) into the final `classes.dex`.
 */
class D8Dexer : DexTool {
    override fun dex(request: DexRequest) {
        val outputIsArchive = request.output.name.endsWith(".zip") || request.output.name.endsWith(".jar")
        if (outputIsArchive) request.output.parentFile?.mkdirs() else request.output.mkdirs()

        val builder = D8Command.builder()
            .setMode(if (request.debuggable) CompilationMode.DEBUG else CompilationMode.RELEASE)
            .setMinApiLevel(request.minApiLevel)
            .setOutput(request.output.toPath(), OutputMode.DexIndexed)

        // D8 accepts class files, jars and dex files/archives, but not a bare classes directory —
        // expand directories to their individual .class/.dex members.
        request.programFiles.filter { it.exists() }.flatMap { expand(it) }
            .forEach { builder.addProgramFiles(it.toPath()) }
        request.libraryFiles.filter { it.exists() }.forEach { builder.addLibraryFiles(it.toPath()) }

        D8.run(builder.build())
    }

    private fun expand(file: File): List<File> = when {
        file.isDirectory -> file.walkTopDown()
            .filter { it.isFile && (it.extension == "class" || it.extension == "dex") }
            .toList()
        else -> listOf(file)
    }
}
