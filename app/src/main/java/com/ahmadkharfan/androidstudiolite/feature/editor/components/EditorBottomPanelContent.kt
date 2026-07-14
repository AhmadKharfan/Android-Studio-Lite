package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLogLine
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
import com.ahmadkharfan.androidstudiolite.feature.editor.AppLogLineUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.DiagnosticUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity

@Composable
fun EditorBottomPanelContent(
    activeTabId: String,
    buildConsole: BuildConsoleState,
    modifier: Modifier = Modifier,
    appLogLines: List<AppLogLineUiModel> = emptyList(),
    diagnostics: List<DiagnosticUiModel> = emptyList(),
    activeFileName: String? = null,
    onCancelBuild: () -> Unit = {},
    onJumpToBuildProblem: (BuildProblem) -> Unit = {},
    onJumpToDiagnostic: (DiagnosticUiModel) -> Unit = {},
) {
    when (activeTabId) {
        "diag" -> DiagnosticsTab(diagnostics, activeFileName, onJumpToDiagnostic, modifier)
        "build" -> BuildTab(buildConsole, onCancelBuild, onJumpToBuildProblem, modifier)
        "logs" -> AppLogsTab(appLogLines, modifier)
        else -> AslEmptyState(
            icon = "terminal",
            title = "Nothing here yet",
            subtitle = "The terminal is coming in a future update.",
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DiagnosticsTab(
    diagnostics: List<DiagnosticUiModel>,
    activeFileName: String?,
    onJumpToDiagnostic: (DiagnosticUiModel) -> Unit,
    modifier: Modifier,
) {
    val colors = AslTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        if (diagnostics.isEmpty()) {
            AslEmptyState(icon = "check", title = "No problems", subtitle = "Analysis found no errors or warnings in this file.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
                items(diagnostics) { d ->
                    val (icon, tint) = when (d.severity) {
                        DiagnosticSeverity.Error -> "octagon-alert" to colors.error
                        DiagnosticSeverity.Warning -> "triangle-alert" to colors.warning
                        DiagnosticSeverity.Info -> "info" to colors.info
                        DiagnosticSeverity.Hint -> "info" to colors.textTertiary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToDiagnostic(d) }
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AslIcon(name = icon, size = 14.dp, tint = tint, modifier = Modifier.padding(top = 1.dp))
                        Text(text = d.message, style = AslCode.codeTiny, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            text = "${activeFileName ?: "file"}:${d.line + 1}:${d.column + 1}",
                            style = AslCode.codeTiny,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
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
    Column(modifier = modifier.fillMaxSize().padding(vertical = 6.dp)) {
        BuildStatusHeader(console, onCancelBuild)
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
                    Text(
                        text = line.text,
                        style = AslCode.codeTiny,
                        color = if (line.isError) colors.error else colors.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildStatusHeader(console: BuildConsoleState, onCancelBuild: () -> Unit) {
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
private fun AppLogsTab(appLogLines: List<AppLogLineUiModel>, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(vertical = 6.dp)) {
        if (appLogLines.isEmpty()) {
            AslEmptyState(icon = "scroll-text", title = "No app logs yet", subtitle = "Run the project to see logcat output here.")
        } else {
            appLogLines.forEach { line ->
                AslLogLine(time = line.time, level = line.level, tag = line.tag, message = line.message)
            }
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
