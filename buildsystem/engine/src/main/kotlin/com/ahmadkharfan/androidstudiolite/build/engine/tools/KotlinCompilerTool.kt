package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.ahmadkharfan.androidstudiolite.build.common.BuildProblem
import com.ahmadkharfan.androidstudiolite.build.common.ProblemSeverity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader

/** Inputs for one Kotlin compilation. */
data class KotlinCompileRequest(
    /** Source files and/or source roots (kotlinc accepts directories). */
    val sources: List<File>,
    val classpath: List<File>,
    val outputDir: File,
    val jvmTarget: String = "11",
    /**
     * Jars of the embedded Kotlin compiler (`kotlin-compiler-embeddable` + deps + stdlib), loaded into
     * an isolated classloader. Shipped as an on-device *data* jar, not a compile-time dependency.
     */
    val compilerClasspath: List<File>,
)

/** In-process Kotlin compilation. Abstracted so the pipeline is testable with a fake compiler. */
fun interface KotlinCompilerTool {
    fun compile(request: KotlinCompileRequest): CompileResult
}

/**
 * Embedded, in-process Kotlin compiler. Rather than depend on the ~50 MB
 * `kotlin-compiler-embeddable` at build time (which would bloat the engine and be awkward to dex), it
 * loads the compiler from a downloaded data jar into an **isolated [URLClassLoader]** and drives
 * `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler` reflectively. Isolation (null parent) keeps the
 * compiler's bundled Kotlin runtime from clashing with the app's.
 */
class EmbeddedKotlinCompiler : KotlinCompilerTool {

    override fun compile(request: KotlinCompileRequest): CompileResult {
        require(request.compilerClasspath.isNotEmpty()) {
            "No Kotlin compiler classpath configured; cannot compile Kotlin sources"
        }
        request.outputDir.mkdirs()

        val args = buildList {
            add("-d"); add(request.outputDir.absolutePath)
            add("-no-stdlib"); add("-no-reflect") // stdlib is supplied via -classpath explicitly
            add("-jvm-target"); add(request.jvmTarget)
            val cp = request.classpath.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }
            if (cp.isNotEmpty()) { add("-classpath"); add(cp) }
            request.sources.forEach { add(it.absolutePath) }
        }

        val buffer = ByteArrayOutputStream()
        val messageStream = PrintStream(buffer, true, "UTF-8")

        val ok = URLClassLoader(request.compilerClasspath.map { it.toURI().toURL() }.toTypedArray(), null).use { loader ->
            val compilerClass = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            val compiler = compilerClass.getDeclaredConstructor().newInstance()
            // ExitCode exec(PrintStream, String...)
            val exec = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
            val exit = exec.invoke(compiler, messageStream, args.toTypedArray())
            exit?.toString() == "OK"
        }

        val raw = buffer.toString("UTF-8")
        return CompileResult(ok, parseProblems(raw), raw)
    }

    private fun parseProblems(output: String): List<BuildProblem> {
        // kotlinc: "path.kt:12:5: error: message"  (also warning:/info:)
        val regex = Regex("""^(.*\.kts?):(\d+):(\d+):\s+(error|warning|info):\s+(.*)$""")
        return output.lines().mapNotNull { line ->
            val m = regex.find(line.trim()) ?: return@mapNotNull null
            BuildProblem(
                severity = when (m.groupValues[4]) {
                    "error" -> ProblemSeverity.ERROR
                    "warning" -> ProblemSeverity.WARNING
                    else -> ProblemSeverity.INFO
                },
                message = m.groupValues[5],
                file = File(m.groupValues[1]),
                line = m.groupValues[2].toIntOrNull(),
                column = m.groupValues[3].toIntOrNull(),
            )
        }
    }
}
