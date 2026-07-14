package com.ahmadkharfan.androidstudiolite.build.engine.pipeline

import com.ahmadkharfan.androidstudiolite.build.engine.tools.Toolchain
import java.io.File

/**
 * The flat, resolved description of one variant build handed to [BuildPipeline]. The app maps its
 * `ProjectModel` (+ dependency resolution) into this; the engine never sees Gradle concepts. Source
 * roots are directories, not individual files, so the compilers pick up newly added sources.
 */
data class BuildSpec(
    val moduleName: String,
    val applicationId: String,
    val minSdk: Int,
    val targetSdk: Int,
    /** Kotlin source roots (may be empty for a Java-only module). */
    val kotlinSourceRoots: List<File>,
    /** Java source roots. */
    val javaSourceRoots: List<File>,
    val resDirs: List<File>,
    val assetsDirs: List<File>,
    val manifest: File,
    /** Resolved compile+runtime classpath: dependency jars and extracted AAR `classes.jar`s. */
    val dependencyJars: List<File>,
    /** Linked resource APKs of AAR dependencies, passed to aapt2 link as `-I`. */
    val dependencyResApks: List<File>,
    val toolchain: Toolchain,
    /** Root for all intermediate build outputs (per variant). */
    val buildDir: File,
    /** Where the final signed APK is written. */
    val outputApk: File,
    val debuggable: Boolean = true,
)

/** Deterministic layout of intermediate outputs under [BuildSpec.buildDir]. */
class BuildLayout(private val buildDir: File) {
    val compiledResDir: File get() = File(buildDir, "res/compiled")
    val resourceApk: File get() = File(buildDir, "res/resources-linked.ap_")
    val generatedJavaDir: File get() = File(buildDir, "res/generated-java")
    val kotlinClassesDir: File get() = File(buildDir, "classes/kotlin")
    val javaClassesDir: File get() = File(buildDir, "classes/java")
    val appDexArchive: File get() = File(buildDir, "dex/app.dex.zip")
    val dexDir: File get() = File(buildDir, "dex/merged")
    val dexCacheDir: File get() = File(buildDir, "dex/libcache")
    val unsignedApk: File get() = File(buildDir, "apk/unsigned.apk")
    val fingerprintStore: File get() = File(buildDir, "fingerprints.txt")
    val keystore: File get() = File(buildDir, "../signing/debug.keystore")

    fun collectCompiledResZips(): List<File> =
        compiledResDir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.sorted() ?: emptyList()

    fun collectDexFiles(): List<File> =
        dexDir.listFiles { f -> f.isFile && f.name.endsWith(".dex") }
            ?.sortedBy { it.name } ?: emptyList()
}
