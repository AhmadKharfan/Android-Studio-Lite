package com.ahmadkharfan.androidstudiolite.build.engine.maven

import java.io.File
import java.util.zip.ZipInputStream

/** The parts of an exploded `.aar` the build pipeline consumes. */
data class ExplodedAar(
    val dir: File,
    /** `classes.jar` — always present in a well-formed AAR; on the compile/dex classpath. */
    val classesJar: File?,
    /** `res/` for aapt2 resource linking, when the library ships resources. */
    val resDir: File?,
    val manifest: File?,
    /** `R.txt` — the library's resource symbols, used when generating dependent R classes. */
    val rTxt: File?,
    /** Extra jars bundled inside the AAR's `libs/` directory. */
    val extraJars: List<File>,
)

/**
 * Explodes an Android `.aar` (a zip) into a directory, extracting the pieces the pipeline needs. The
 * exploded form is content-addressed by the caller (keyed on the aar's hash) so it is only unpacked
 * once. Cleanroom: a plain zip read, no Android build-tools dependency.
 */
object AarExtractor {

    fun explode(aar: File, into: File): ExplodedAar {
        into.mkdirs()
        ZipInputStream(aar.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val target = File(into, entry.name)
                // Guard against zip-slip: the resolved path must stay inside `into`.
                if (!target.canonicalPath.startsWith(into.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                target.outputStream().buffered().use { out -> zip.copyTo(out) }
            }
        }
        return describe(into)
    }

    /** Build an [ExplodedAar] descriptor for an already-exploded directory (no extraction). */
    fun describe(dir: File): ExplodedAar {
        val libs = File(dir, "libs").listFiles { f -> f.isFile && f.extension == "jar" }?.toList() ?: emptyList()
        return ExplodedAar(
            dir = dir,
            classesJar = File(dir, "classes.jar").takeIf { it.isFile },
            resDir = File(dir, "res").takeIf { it.isDirectory },
            manifest = File(dir, "AndroidManifest.xml").takeIf { it.isFile },
            rTxt = File(dir, "R.txt").takeIf { it.isFile },
            extraJars = libs,
        )
    }
}
