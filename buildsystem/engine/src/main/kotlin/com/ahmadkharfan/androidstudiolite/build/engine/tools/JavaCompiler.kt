package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.ahmadkharfan.androidstudiolite.build.common.BuildProblem
import com.ahmadkharfan.androidstudiolite.build.common.ProblemSeverity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import org.eclipse.jdt.core.compiler.batch.BatchCompiler

/** Inputs for a single Java compilation. */
data class JavaCompileRequest(
    val sourceFiles: List<File>,
    /** Additional source roots (e.g. generated `R.java`), compiled alongside [sourceFiles]. */
    val sourceRoots: List<File> = emptyList(),
    val classpath: List<File>,
    /** `android.jar` — the bootclasspath the app compiles against. */
    val bootClasspath: List<File>,
    val outputDir: File,
    val sourceVersion: String = "11",
)

/** Outcome of a compilation: success plus any structured problems parsed from the compiler output. */
data class CompileResult(
    val success: Boolean,
    val problems: List<BuildProblem>,
    val rawOutput: String,
)

/** In-process Java compilation. Abstracted so the pipeline is testable with a fake compiler. */
fun interface JavaCompilerTool {
    fun compile(request: JavaCompileRequest): CompileResult
}

/**
 * ECJ-backed [JavaCompilerTool] (Eclipse Compiler for Java, EPL-2.0). Runs entirely in-process — no
 * `javac` binary needed — which is exactly why it fits the play flavor. Problems are parsed from
 * ECJ's textual diagnostics into [BuildProblem]s for file:line navigation.
 */
class EcjJavaCompiler : JavaCompilerTool {

    override fun compile(request: JavaCompileRequest): CompileResult {
        request.outputDir.mkdirs()
        val out = StringWriter()
        val err = StringWriter()

        val args = buildList {
            add("-${request.sourceVersion}")           // source+target level, e.g. -11
            add("-proc:none")                            // no annotation processing in the minimal subset
            add("-nowarn")
            add("-d"); add(request.outputDir.absolutePath)
            val cp = request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
            if (cp.isNotEmpty()) { add("-classpath"); add(cp) }
            val bcp = request.bootClasspath.joinToString(File.pathSeparator) { it.absolutePath }
            if (bcp.isNotEmpty()) { add("-bootclasspath"); add(bcp) }
            // Source files and source roots.
            request.sourceRoots.forEach { add(it.absolutePath) }
            request.sourceFiles.forEach { add(it.absolutePath) }
        }

        val success = PrintWriter(out).use { o ->
            PrintWriter(err).use { e ->
                BatchCompiler.compile(args.toTypedArray(), o, e, null)
            }
        }
        val raw = buildString {
            append(out.toString())
            append(err.toString())
        }
        return CompileResult(success, parseProblems(raw), raw)
    }

    private fun parseProblems(output: String): List<BuildProblem> {
        // ECJ diagnostics look like:  1. ERROR in /path/Foo.java (at line 12)
        val header = Regex("""\d+\.\s+(ERROR|WARNING|INFO)\s+in\s+(.+?)\s+\(at line (\d+)\)""")
        val problems = ArrayList<BuildProblem>()
        val lines = output.lines()
        for (i in lines.indices) {
            val m = header.find(lines[i]) ?: continue
            val severity = when (m.groupValues[1]) {
                "ERROR" -> ProblemSeverity.ERROR
                "WARNING" -> ProblemSeverity.WARNING
                else -> ProblemSeverity.INFO
            }
            // The message is usually a couple of lines below the caret marker.
            val message = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().all { c -> c == '^' } }
                ?.trim().orEmpty()
            problems += BuildProblem(
                severity = severity,
                message = message.ifEmpty { lines[i].trim() },
                file = File(m.groupValues[2].trim()),
                line = m.groupValues[3].toIntOrNull(),
            )
        }
        return problems
    }
}
