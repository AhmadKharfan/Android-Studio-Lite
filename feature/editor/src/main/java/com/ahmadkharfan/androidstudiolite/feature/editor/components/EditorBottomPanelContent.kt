package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslBuildOutputLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTaskStatus
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.terminal.EditorEmbeddedTerminal

@Composable
fun EditorBottomPanelContent(
    activeTabId: String,
    buildConsole: BuildConsoleState,
    modifier: Modifier = Modifier,
    projectRootPath: String = "",
    onCancelBuild: () -> Unit = {},
    onJumpToBuildProblem: (BuildProblem) -> Unit = {},
) {
    when (activeTabId) {
        "build" -> BuildTab(buildConsole, onCancelBuild, onJumpToBuildProblem, modifier)
        "term" -> EditorEmbeddedTerminal(projectRootPath = projectRootPath, modifier = modifier)
        else -> AslEmptyState(
            icon = "terminal",
            title = "Nothing here yet",
            subtitle = "The terminal is coming in a future update.",
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BuildTab(
    console: BuildConsoleState,
    onCancelBuild: () -> Unit,
    onJumpToBuildProblem: (BuildProblem) -> Unit,
    modifier: Modifier,
) {
    val colors = AslTheme.colors
    val isEmpty = console.status == BuildStatus.Idle && console.taskGroups.isEmpty() && console.logs.isEmpty()
    if (isEmpty) {
        AslEmptyState(
            icon = "hammer",
            title = "No build output yet",
            subtitle = "Run the project to see the build output here.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxSize().padding(vertical = 6.dp)) {
        BuildStatusHeader(
            console = console,
            onCancelBuild = onCancelBuild,
            onCopyAll = { clipboard.setText(AnnotatedString(console.toClipboardText())) },
        )
        // NOTE: do NOT wrap this LazyColumn in a single SelectionContainer. A shared
        // SelectionContainer over a lazy list crashes when the user selects text and then
        // scrolls — disposed items drop their selectable ids from the selection registry, and
        // the selection manager then looks up a missing id
        // (java.util.NoSuchElementException: Cannot find value for key N). Instead we offer a
        // reliable "Copy" action for the whole console and per-line SelectionContainers (each
        // with its own registry) for granular copying.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (console.problems.isNotEmpty()) {
                item { SectionLabel("Problems (${console.problems.size})") }
                items(console.problems) { problem -> ProblemRow(problem, onJumpToBuildProblem) }
            }
            if (console.taskGroups.isNotEmpty()) {
                item { SectionLabel("Tasks") }
                console.taskGroups.forEach { group ->
                    if (group.module.isNotEmpty()) {
                        item { AslBuildOutputLine(text = group.module, depth = 0) }
                    }
                    items(group.tasks) { task ->
                        AslBuildOutputLine(
                            text = task.name,
                            depth = if (group.module.isEmpty()) 0 else 1,
                            status = task.result.toTaskStatus(),
                        )
                    }
                }
            }
            if (console.logs.isNotEmpty()) {
                item { SectionLabel("Output") }
                items(console.logs) { line ->
                    // Per-line SelectionContainer: each has its own selection registry, so a line
                    // scrolling out of view can never leave a dangling selectable id behind.
                    SelectionContainer {
                        Text(
                            text = line.text,
                            style = AslCode.codeTiny,
                            color = if (line.isError) colors.error else colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Flattens the whole console into plain text for the "Copy" action. */
private fun BuildConsoleState.toClipboardText(): String = buildString {
    val statusLabel = when (status) {
        BuildStatus.Running -> progressMessage ?: "Building…"
        BuildStatus.Succeeded -> "Build successful"
        BuildStatus.Failed -> "Build failed"
        BuildStatus.Cancelled -> "Build cancelled"
        BuildStatus.Idle -> "Ready"
    }
    appendLine(statusLabel)
    if (problems.isNotEmpty()) {
        appendLine()
        appendLine("Problems (${problems.size})")
        problems.forEach { problem ->
            append("  [").append(problem.severity.name).append("] ").append(problem.message)
            problem.location?.let { append(" (").append(it).append(")") }
            appendLine()
        }
    }
    if (taskGroups.isNotEmpty()) {
        appendLine()
        appendLine("Tasks")
        taskGroups.forEach { group ->
            if (group.module.isNotEmpty()) appendLine(group.module)
            group.tasks.forEach { task ->
                append("  ").append(task.name)
                task.result?.let { append(" — ").append(it.name) }
                appendLine()
            }
        }
    }
    if (logs.isNotEmpty()) {
        appendLine()
        appendLine("Output")
        logs.forEach { appendLine(it.text) }
    }
}

@Composable
private fun BuildStatusHeader(
    console: BuildConsoleState,
    onCancelBuild: () -> Unit,
    onCopyAll: () -> Unit,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (label, tint) = when (console.status) {
                BuildStatus.Running -> (console.progressMessage ?: "Building…") to colors.info
                BuildStatus.Succeeded -> "Build successful" to colors.success
                BuildStatus.Failed -> "Build failed" to colors.error
                BuildStatus.Cancelled -> "Build cancelled" to colors.textTertiary
                BuildStatus.Idle -> "Ready" to colors.textTertiary
            }
            Text(
                text = label,
                style = AslCode.codeSmall,
                color = tint,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            console.durationMillis?.let {
                Text(text = String.format("%.1fs", it / 1000.0), style = AslCode.codeTiny, color = colors.textTertiary)
            }
            val hasContent = console.problems.isNotEmpty() || console.taskGroups.isNotEmpty() || console.logs.isNotEmpty()
            if (hasContent) {
                AslButton(
                    label = "Copy",
                    onClick = onCopyAll,
                    variant = AslButtonVariant.Secondary,
                    size = AslButtonSize.Md,
                    icon = "copy",
                )
            }
            if (console.isRunning) {
                AslButton(
                    label = "Cancel",
                    onClick = onCancelBuild,
                    variant = AslButtonVariant.Secondary,
                    size = AslButtonSize.Md,
                    icon = "x",
                )
            }
        }
        if (console.isRunning) {
            AslLinearProgress(
                label = null,
                detail = if (console.taskCount > 0) "${console.finishedTaskCount}/${console.taskCount} tasks" else null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun ProblemRow(problem: BuildProblem, onJump: (BuildProblem) -> Unit) {
    val colors = AslTheme.colors
    val (icon, tint) = when (problem.severity) {
        BuildEvent.ProblemSeverity.ERROR -> "octagon-alert" to colors.error
        BuildEvent.ProblemSeverity.WARNING -> "triangle-alert" to colors.warning
        BuildEvent.ProblemSeverity.INFO -> "info" to colors.info
    }
    val clickable = if (problem.filePath != null) Modifier.clickable { onJump(problem) } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AslIcon(name = icon, size = 14.dp, tint = tint, modifier = Modifier.padding(top = 1.dp))
        Text(text = problem.message, style = AslCode.codeTiny, color = colors.textPrimary, modifier = Modifier.weight(1f))
        problem.location?.let {
            Text(text = it, style = AslCode.codeTiny, color = colors.info)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AslCode.codeTiny,
        color = AslTheme.colors.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private fun BuildEvent.TaskResult?.toTaskStatus(): AslTaskStatus? = when (this) {
    BuildEvent.TaskResult.SUCCESS -> AslTaskStatus.Success
    BuildEvent.TaskResult.UP_TO_DATE -> AslTaskStatus.Skipped
    BuildEvent.TaskResult.SKIPPED -> AslTaskStatus.Skipped
    BuildEvent.TaskResult.FAILED -> AslTaskStatus.Failed
    null -> AslTaskStatus.Running
}
