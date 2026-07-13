package com.ahmadkharfan.androidstudiolite.build.engine.pipeline

import com.ahmadkharfan.androidstudiolite.build.common.Fingerprint
import com.ahmadkharfan.androidstudiolite.build.engine.maven.AarExtractor
import com.ahmadkharfan.androidstudiolite.build.engine.maven.ResolutionResult
import java.io.File

/**
 * Flattens a [ResolutionResult] into what the pipeline consumes: a compile/dex classpath of jars and,
 * for AAR dependencies, their exploded `res/` directories (fed to aapt2). AARs are exploded once into
 * a content-addressed directory so repeat builds reuse the unpacked form.
 */
object DependencyClasspath {

    data class Result(
        /** Jars for the compile + dex classpath (plain jars and each AAR's `classes.jar` + inner libs). */
        val compileJars: List<File>,
        /** `res/` directories contributed by AAR dependencies. */
        val aarResDirs: List<File>,
        val warnings: List<String>,
    )

    fun build(resolution: ResolutionResult, explodeRoot: File): Result {
        val jars = ArrayList<File>()
        val resDirs = ArrayList<File>()
        val warnings = ArrayList(resolution.warnings)

        for (artifact in resolution.artifacts) {
            when (artifact.packaging) {
                "aar" -> {
                    val key = Fingerprint.ofFile(artifact.file)
                    val dir = File(explodeRoot, "${artifact.coordinate.artifact}-$key")
                    val exploded = if (dir.resolve(".ok").isFile) {
                        AarExtractor.describe(dir)
                    } else {
                        dir.deleteRecursively()
                        AarExtractor.explode(artifact.file, dir).also { dir.resolve(".ok").writeText("") }
                    }
                    exploded.classesJar?.let { jars += it }
                    jars += exploded.extraJars
                    exploded.resDir?.let { resDirs += it }
                }
                else -> jars += artifact.file // jar, bundle, …
            }
        }
        return Result(jars, resDirs, warnings)
    }
}
