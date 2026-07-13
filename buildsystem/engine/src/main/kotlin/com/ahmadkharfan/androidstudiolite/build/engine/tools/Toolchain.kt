package com.ahmadkharfan.androidstudiolite.build.engine.tools

import java.io.File

/**
 * The on-device toolchain the play-flavor engine needs, resolved from [IdeEnvironmentPaths] and the
 * app's `jniLibs`. Everything here is *data* or a single execable native binary (aapt2), keeping the
 * play flavor Play-policy-safe — the JVM tools (ECJ, D8/R8, apksig, kotlinc) all run in-process.
 *
 * @param aapt2Binary the aapt2 executable extracted to `nativeLibraryDir` (exec-safe).
 * @param androidJar  `android.jar` for the target platform — the compile bootclasspath.
 * @param kotlinCompilerClasspath jars of the embedded Kotlin compiler, loaded in-process on demand.
 *                                Empty when the project has no Kotlin sources.
 * @param coreLambdaStubsJar optional `core-lambda-stubs.jar` desugaring support (unused for the
 *                           minimal templates; reserved).
 */
data class Toolchain(
    val aapt2Binary: File?,
    val androidJar: File,
    val kotlinCompilerClasspath: List<File> = emptyList(),
    val coreLambdaStubsJar: File? = null,
) {
    fun requireAapt2(): File =
        aapt2Binary?.takeIf { it.exists() }
            ?: error("aapt2 binary is not available (expected in the app's nativeLibraryDir)")
}
