package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildConsoleState

private val WHITESPACE_AFTER_DOT = Regex("\\.\\s+")

fun sanitizeFileEntryName(raw: String): String = raw.replace(WHITESPACE_AFTER_DOT, ".")

fun variantLabel(variant: String): String = variant.replaceFirstChar { it.uppercase() }

fun gradleAssembleTaskName(variant: String): String = "assemble${variantLabel(variant)}"

fun BuildConsoleState.toClipboardText(
    statusLabel: String,
    problemsLabel: String,
    tasksLabel: String,
    outputLabel: String,
): String = buildString {
    appendLine(statusLabel)
    if (problems.isNotEmpty()) {
        appendLine()
        appendLine(problemsLabel)
        problems.forEach { problem ->
            append("  [").append(problem.severity.name).append("] ").append(problem.message)
            problem.location?.let { append(" (").append(it).append(")") }
            appendLine()
        }
    }
    if (taskGroups.isNotEmpty()) {
        appendLine()
        appendLine(tasksLabel)
        taskGroups.forEach { group ->
            if (group.module.isNotEmpty()) appendLine(group.module)
            group.tasks.forEach { task ->
                append("  ").append(task.name)
                task.result?.let { append(" ").append(it.name) }
                appendLine()
            }
        }
    }
    if (logs.isNotEmpty()) {
        appendLine()
        appendLine(outputLabel)
        logs.forEach { appendLine(it.text) }
    }
}
