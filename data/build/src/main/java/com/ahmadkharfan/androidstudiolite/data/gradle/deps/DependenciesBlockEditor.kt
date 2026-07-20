package com.ahmadkharfan.androidstudiolite.data.gradle.deps

import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GTokenType
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GradleScriptScanner

/**
 * Structured edits to the `dependencies {}` block of a `build.gradle(.kts)`. Pure string→string;
 * the caller owns file I/O. Insertions match the script's DSL so KTS gets `implementation("…")`
 * and Groovy gets `implementation "…"`.
 */
object DependenciesBlockEditor {

    sealed interface Result {
        data class Changed(val text: String) : Result
        data class Unchanged(val reason: String) : Result
        data class Failure(val reason: String) : Result
    }

    /**
     * Adds a dependency line. [notation] is what goes inside/after the configuration call — a
     * coordinate `"g:a:v"`, a catalog accessor `libs.foo`, or `project(":x")`. Pass [alreadyQuoted]
     * = false for a raw coordinate so it gets quoted; true for catalog/project notations.
     */
    fun add(
        text: String,
        configuration: String,
        notation: String,
        dsl: GradleDsl,
        quoteNotation: Boolean,
    ): Result {
        val tokens = GradleScriptScanner.tokenize(text)
        val body = GradleScriptScanner.findBlockBody(tokens, "dependencies")

        val rendered = renderNotation(notation, quoteNotation)
        val line = when (dsl) {
            GradleDsl.KOTLIN -> "$configuration($rendered)"
            GradleDsl.GROOVY -> "$configuration $rendered"
        }

        if (body != null && containsNotation(text, tokens, body, notation)) {
            return Result.Unchanged("Dependency '$notation' already declared")
        }

        return if (body == null) {
            // No dependencies block — append a fresh one.
            val sb = StringBuilder(text.trimEnd('\n'))
            sb.append("\n\ndependencies {\n    ").append(line).append("\n}\n")
            Result.Changed(sb.toString())
        } else {
            val insertOffset = insertionOffset(tokens, body, text)
            val indent = detectIndent(text, tokens, body)
            val edited = StringBuilder(text)
                .insert(insertOffset, "\n$indent$line")
                .toString()
            Result.Changed(edited)
        }
    }

    /** Removes any dependency line whose notation contains [notation] (coordinate or accessor). */
    fun remove(text: String, notation: String): Result {
        val lines = text.split('\n')
        val kept = lines.filterNot { line ->
            val t = line.trim()
            looksLikeDependencyLine(t) && t.contains(notation)
        }
        if (kept.size == lines.size) return Result.Unchanged("Dependency '$notation' not found")
        return Result.Changed(kept.joinToString("\n"))
    }

    private fun renderNotation(notation: String, quote: Boolean): String =
        if (quote) "\"$notation\"" else notation

    private fun containsNotation(
        text: String,
        tokens: List<com.ahmadkharfan.androidstudiolite.data.gradle.parse.GToken>,
        body: IntRange,
        notation: String,
    ): Boolean {
        val start = tokens.getOrNull(body.first)?.start ?: return false
        val end = tokens.getOrNull(body.last)?.end ?: return false
        return text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length)).contains(notation)
    }

    /** Offset just after the last existing statement in the block (before the closing brace). */
    private fun insertionOffset(
        tokens: List<com.ahmadkharfan.androidstudiolite.data.gradle.parse.GToken>,
        body: IntRange,
        text: String,
    ): Int {
        // Find the last non-newline token inside the block and insert right after it.
        for (i in body.last downTo body.first) {
            if (tokens[i].type != GTokenType.NEWLINE) return tokens[i].end
        }
        // Empty block: insert right after the opening brace.
        return tokens.getOrNull(body.first - 1)?.end ?: tokens[body.first].start
    }

    private fun detectIndent(
        text: String,
        tokens: List<com.ahmadkharfan.androidstudiolite.data.gradle.parse.GToken>,
        body: IntRange,
    ): String {
        // Reuse the indentation of the first statement in the block, if any.
        val firstStmt = (body.first..body.last).firstOrNull { tokens[it].type != GTokenType.NEWLINE }
            ?: return "    "
        val lineStart = text.lastIndexOf('\n', tokens[firstStmt].start).let { if (it < 0) 0 else it + 1 }
        val prefix = text.substring(lineStart, tokens[firstStmt].start)
        return prefix.takeWhile { it == ' ' || it == '\t' }.ifEmpty { "    " }
    }

    private val CONFIG_PREFIXES = listOf(
        "implementation", "api", "compileOnly", "runtimeOnly", "annotationProcessor",
        "kapt", "ksp", "testImplementation", "androidTestImplementation", "debugImplementation",
        "releaseImplementation", "testRuntimeOnly", "coreLibraryDesugaring",
    )

    private fun looksLikeDependencyLine(trimmed: String): Boolean =
        CONFIG_PREFIXES.any { trimmed.startsWith("$it(") || trimmed.startsWith("$it ") }
}
