package com.ahmadkharfan.androidstudiolite.data.githubactions.logs

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File

/**
 * Finds compiler and resource errors in plain Gradle console output, so a build that only returns a
 * log still reports problems the editor can jump to.
 *
 * Paths in the log are the build machine's; everything up to and including [workspace] is removed
 * and the rest is resolved against [projectRoot]. A path outside the workspace is kept without a file.
 */
internal class GradleProblemParser(
    private val projectRoot: File,
    private val workspace: String,
) {

    fun parse(line: String): BuildEvent.Problem? {
        val text = line.trim()
        return FORMATS.firstNotNullOfOrNull { format -> format.regex.matchEntire(text)?.let { format.toProblem(it) } }
    }

    private fun Format.toProblem(match: MatchResult): BuildEvent.Problem {
        fun group(index: Int?) = index?.let { match.groupValues[it] }
        return BuildEvent.Problem(
            severity = when (group(level)?.lowercase() ?: "error") {
                "e", "error" -> BuildEvent.ProblemSeverity.ERROR
                "w", "warning" -> BuildEvent.ProblemSeverity.WARNING
                else -> BuildEvent.ProblemSeverity.INFO
            },
            message = match.groupValues[message].trim(),
            file = localFile(match.groupValues[path]),
            line = group(row)?.toIntOrNull(),
            column = group(column)?.toIntOrNull(),
        )
    }

    private fun localFile(path: String): File? {
        val clean = path.removePrefix("file://").trim()
        val prefix = workspace.trimEnd('/') + "/"
        if (!clean.startsWith(prefix)) return null
        return File(projectRoot, clean.removePrefix(prefix))
    }

    /** One log format; the ints are the regex groups holding each part (null when absent). */
    private class Format(
        val regex: Regex,
        val level: Int?,
        val path: Int,
        val row: Int?,
        val column: Int?,
        val message: Int,
    )

    private companion object {
        val FORMATS = listOf(
            // Kotlin 2: `e: file:///work/app/src/Main.kt:20:9 Unresolved reference 'foo'.`
            Format(Regex("""^([ew]): (file://\S+?):(\d+):(\d+) (.+)$"""), level = 1, path = 2, row = 3, column = 4, message = 5),
            // Kotlin 1.x: `e: /work/app/src/Main.kt: (20, 9): Unresolved reference: foo`
            Format(Regex("""^([ew]): (/\S+?): \((\d+), (\d+)\): (.+)$"""), level = 1, path = 2, row = 3, column = 4, message = 5),
            // javac: `/work/app/src/Foo.java:12: error: cannot find symbol`
            Format(Regex("""^(/\S+?\.java):(\d+): (error|warning): (.+)$"""), level = 3, path = 1, row = 2, column = null, message = 4),
            // AAPT2: `ERROR: /work/app/src/main/res/layout/a.xml:5: AAPT: error: attribute x not found.`
            Format(Regex("""^ERROR:\s*(/\S+?):(\d+): AAPT: (?:error: )?(.+)$"""), level = null, path = 1, row = 2, column = null, message = 3),
        )
    }
}
