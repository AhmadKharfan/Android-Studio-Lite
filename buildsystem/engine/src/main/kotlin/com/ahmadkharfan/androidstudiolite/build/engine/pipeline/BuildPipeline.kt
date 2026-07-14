package com.ahmadkharfan.androidstudiolite.build.engine.pipeline

import com.ahmadkharfan.androidstudiolite.build.common.BuildFailedException
import com.ahmadkharfan.androidstudiolite.build.common.BuildProblem
import com.ahmadkharfan.androidstudiolite.build.common.BuildReporter
import com.ahmadkharfan.androidstudiolite.build.common.BuildTask
import com.ahmadkharfan.androidstudiolite.build.common.CancellationToken
import com.ahmadkharfan.androidstudiolite.build.common.FingerprintStore
import com.ahmadkharfan.androidstudiolite.build.common.LogStream
import com.ahmadkharfan.androidstudiolite.build.common.ProblemSeverity
import com.ahmadkharfan.androidstudiolite.build.common.TaskContext
import com.ahmadkharfan.androidstudiolite.build.common.TaskExecutor
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2LinkRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2Tool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.ApkPackager
import com.ahmadkharfan.androidstudiolite.build.engine.tools.ApkSignerTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DebugKeystore
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DexArchiveCache
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DexRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DexTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.JavaCompileRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.JavaCompilerTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.KotlinCompileRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.KotlinCompilerTool
import java.io.File

/**
 * The play-flavor in-process build pipeline: a linear task graph that turns a [BuildSpec] into an
 * installable, signed debug APK —
 *
 * ```
 * aapt2 compile → aapt2 link (+R.java) → kotlinc → ECJ javac → D8 (per-jar cache) → merge → package → apksig
 * ```
 *
 * Every tool is injected behind an interface so the orchestration is unit-testable with fakes; the
 * real [com.ahmadkharfan.androidstudiolite.build.engine.tools] implementations run on ART. Tasks
 * declare fingerprintable inputs/outputs, so an unchanged step is skipped on rebuild (see
 * [com.ahmadkharfan.androidstudiolite.build.common.TaskExecutor]).
 */
class BuildPipeline(
    private val aapt2: Aapt2Tool,
    private val kotlinc: KotlinCompilerTool,
    private val javac: JavaCompilerTool,
    private val dexTool: DexTool,
    private val signer: ApkSignerTool,
    private val packager: ApkPackager = ApkPackager(),
) {
    /** Build the signed APK. @return the signed APK file ([BuildSpec.outputApk]). */
    fun run(spec: BuildSpec, reporter: BuildReporter, cancellation: CancellationToken): File {
        val layout = BuildLayout(spec.buildDir)
        val store = FingerprintStore.load(layout.fingerprintStore)
        val executor = TaskExecutor(store, reporter, cancellation)
        executor.run(tasks(spec, layout))
        return spec.outputApk
    }

    /** The ordered task list — exposed for testing and for a dry-run/plan view. */
    fun tasks(spec: BuildSpec, layout: BuildLayout = BuildLayout(spec.buildDir)): List<BuildTask> {
        val hasKotlin = spec.kotlinSourceRoots.any { it.exists() } &&
            spec.toolchain.kotlinCompilerClasspath.isNotEmpty()
        val tasks = ArrayList<BuildTask>()
        tasks += CompileResourcesTask(spec, layout)
        tasks += LinkResourcesTask(spec, layout)
        if (hasKotlin) tasks += KotlinCompileTask(spec, layout)
        tasks += JavaCompileTask(spec, layout, kotlinOnClasspath = hasKotlin)
        tasks += DexTask(spec, layout, includeKotlin = hasKotlin)
        tasks += PackageApkTask(spec, layout)
        tasks += SignApkTask(spec, layout)
        return tasks
    }

    private fun path(spec: BuildSpec, step: String) = ":${spec.moduleName}:$step"

    // ---- resources --------------------------------------------------------------------------

    private inner class CompileResourcesTask(val spec: BuildSpec, val layout: BuildLayout) : BuildTask {
        override val path = path(spec, "compile${spec.variantTag()}Resources")
        override val inputFiles = spec.resDirs
        override val inputValues = spec.resDirs.map { it.absolutePath }
        override val outputFiles = listOf(layout.compiledResDir)

        override fun run(context: TaskContext) {
            layout.compiledResDir.deleteRecursively()
            layout.compiledResDir.mkdirs()
            spec.resDirs.filter { it.isDirectory }.forEachIndexed { index, resDir ->
                context.throwIfCancelled()
                val out = File(layout.compiledResDir, "res-$index.zip")
                val result = aapt2.compile(resDir, out)
                if (result.output.isNotBlank()) context.log(result.output, LogStream.STDERR)
                if (!result.success) fail(context, "aapt2 compile failed for $resDir", result.output)
            }
        }
    }

    private inner class LinkResourcesTask(val spec: BuildSpec, val layout: BuildLayout) : BuildTask {
        override val path = path(spec, "link${spec.variantTag()}Resources")
        // Lazy: the compiled-res zips are produced by the preceding task in this same run, so this
        // must be evaluated when the executor reads it (post-compile), not at construction time.
        override val inputFiles get() = layout.collectCompiledResZips() + spec.manifest +
            spec.toolchain.androidJar + spec.dependencyResApks
        override val inputValues = listOf("minSdk=${spec.minSdk}", "targetSdk=${spec.targetSdk}")
        override val outputFiles = listOf(layout.resourceApk, layout.generatedJavaDir)

        override fun run(context: TaskContext) {
            val result = aapt2.link(
                Aapt2LinkRequest(
                    compiledResources = layout.collectCompiledResZips(),
                    manifest = spec.manifest,
                    androidJar = spec.toolchain.androidJar,
                    outputApk = layout.resourceApk,
                    javaOutputDir = layout.generatedJavaDir,
                    minSdk = spec.minSdk,
                    targetSdk = spec.targetSdk,
                    extraInputApks = spec.dependencyResApks,
                ),
            )
            if (result.output.isNotBlank()) context.log(result.output, LogStream.STDERR)
            if (!result.success) fail(context, "aapt2 link failed", result.output)
        }
    }

    // ---- compilation ------------------------------------------------------------------------

    private inner class KotlinCompileTask(val spec: BuildSpec, val layout: BuildLayout) : BuildTask {
        override val path = path(spec, "compile${spec.variantTag()}Kotlin")
        override val inputFiles = spec.kotlinSourceRoots + spec.dependencyJars + spec.toolchain.androidJar
        override val outputFiles = listOf(layout.kotlinClassesDir)

        override fun run(context: TaskContext) {
            layout.kotlinClassesDir.deleteRecursively()
            val result = kotlinc.compile(
                KotlinCompileRequest(
                    sources = spec.kotlinSourceRoots.filter { it.exists() },
                    classpath = spec.dependencyJars + spec.toolchain.androidJar,
                    outputDir = layout.kotlinClassesDir,
                    compilerClasspath = spec.toolchain.kotlinCompilerClasspath,
                ),
            )
            result.problems.forEach { context.problem(it) }
            if (!result.success) throw BuildFailedException("Kotlin compilation failed", problems = result.problems)
        }
    }

    private inner class JavaCompileTask(
        val spec: BuildSpec,
        val layout: BuildLayout,
        val kotlinOnClasspath: Boolean,
    ) : BuildTask {
        override val path = path(spec, "compile${spec.variantTag()}Java")
        override val inputFiles = spec.javaSourceRoots + layout.generatedJavaDir +
            spec.dependencyJars + spec.toolchain.androidJar +
            (if (kotlinOnClasspath) listOf(layout.kotlinClassesDir) else emptyList())
        override val outputFiles = listOf(layout.javaClassesDir)

        override fun run(context: TaskContext) {
            layout.javaClassesDir.deleteRecursively()
            val classpath = spec.dependencyJars +
                (if (kotlinOnClasspath) listOf(layout.kotlinClassesDir) else emptyList())
            val result = javac.compile(
                JavaCompileRequest(
                    sourceFiles = emptyList(),
                    sourceRoots = (spec.javaSourceRoots + layout.generatedJavaDir).filter { it.exists() },
                    classpath = classpath,
                    bootClasspath = listOf(spec.toolchain.androidJar),
                    outputDir = layout.javaClassesDir,
                ),
            )
            result.problems.forEach { context.problem(it) }
            if (!result.success) throw BuildFailedException("Java compilation failed", problems = result.problems)
        }
    }

    // ---- dexing -----------------------------------------------------------------------------

    private inner class DexTask(
        val spec: BuildSpec,
        val layout: BuildLayout,
        val includeKotlin: Boolean,
    ) : BuildTask {
        override val path = path(spec, "dex${spec.variantTag()}")
        override val inputFiles = buildList {
            if (includeKotlin) add(layout.kotlinClassesDir)
            add(layout.javaClassesDir)
            addAll(spec.dependencyJars)
        }
        override val inputValues = listOf("minSdk=${spec.minSdk}")
        override val outputFiles = listOf(layout.dexDir)

        override fun run(context: TaskContext) {
            layout.dexDir.deleteRecursively()
            val androidJar = spec.toolchain.androidJar

            // 1. Per-library dex archives (content-hash cached).
            val cache = DexArchiveCache(layout.dexCacheDir, dexTool)
            val libArchives = spec.dependencyJars.filter { it.exists() }.map {
                context.throwIfCancelled()
                cache.dexLibrary(it, spec.minSdk, androidJar)
            }

            // 2. Dex the app's own classes.
            val appClassDirs = buildList {
                if (includeKotlin) add(layout.kotlinClassesDir)
                add(layout.javaClassesDir)
            }.filter { it.exists() }
            dexTool.dex(
                DexRequest(
                    programFiles = appClassDirs,
                    libraryFiles = listOf(androidJar),
                    minApiLevel = spec.minSdk,
                    output = layout.appDexArchive,
                    debuggable = spec.debuggable,
                ),
            )

            // 3. Merge app dex + library dex archives into the final classes*.dex.
            context.throwIfCancelled()
            dexTool.dex(
                DexRequest(
                    programFiles = listOf(layout.appDexArchive) + libArchives,
                    libraryFiles = listOf(androidJar),
                    minApiLevel = spec.minSdk,
                    output = layout.dexDir,
                    debuggable = spec.debuggable,
                ),
            )
        }
    }

    // ---- package & sign ---------------------------------------------------------------------

    private inner class PackageApkTask(val spec: BuildSpec, val layout: BuildLayout) : BuildTask {
        override val path = path(spec, "package${spec.variantTag()}")
        override val inputFiles = listOf(layout.resourceApk, layout.dexDir) + spec.assetsDirs
        override val outputFiles = listOf(layout.unsignedApk)

        override fun run(context: TaskContext) {
            packager.buildUnsignedApk(
                baseApk = layout.resourceApk.takeIf { it.isFile },
                dexFiles = layout.collectDexFiles(),
                output = layout.unsignedApk,
                assetsDir = spec.assetsDirs.firstOrNull { it.isDirectory },
                rawManifest = spec.manifest.takeIf { !layout.resourceApk.isFile },
            )
        }
    }

    private inner class SignApkTask(val spec: BuildSpec, val layout: BuildLayout) : BuildTask {
        override val path = path(spec, "sign${spec.variantTag()}")
        override val inputFiles = listOf(layout.unsignedApk, layout.keystore)
        override val outputFiles = listOf(spec.outputApk)

        override fun run(context: TaskContext) {
            val key = DebugKeystore(layout.keystore).load()
            signer.sign(layout.unsignedApk, spec.outputApk, key, spec.minSdk)
            context.log("Signed APK: ${spec.outputApk.absolutePath}")
        }
    }

    private fun fail(context: TaskContext, message: String, output: String): Nothing {
        val problem = BuildProblem(ProblemSeverity.ERROR, message)
        context.problem(problem)
        throw BuildFailedException("$message\n$output", problems = listOf(problem))
    }
}

/** Capitalised variant tag for task paths; derived from the module for the single-variant subset. */
private fun BuildSpec.variantTag(): String = "Debug"
