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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.core.format.formatBytes
import com.ahmadkharfan.androidstudiolite.core.format.formatSeconds
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslBuildOutputLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTaskStatus
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildArtifact
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildLogLine
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildProblem
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.BuildTaskGroup
import com.ahmadkharfan.androidstudiolite.feature.editor.R
import com.ahmadkharfan.androidstudiolite.feature.editor.toClipboardText

@Composable
fun EditorBottomPanelContent(
    activeTabId: String,
    buildConsole: BuildConsoleState,
    modifier: Modifier = Modifier,
    projectRootPath: String = "",
    onJumpToBuildProblem: (BuildProblem) -> Unit = {},
    // The terminal is supplied by the host rather than imported: the editor describes where a
    // terminal goes, and :app decides which implementation fills it.
    terminalContent: @Composable (projectRootPath: String, modifier: Modifier) -> Unit = { _, _ -> },
) {
    when (activeTabId) {
        "build" -> BuildTab(buildConsole, onJumpToBuildProblem, modifier)
        "term" -> terminalContent(projectRootPath, modifier)
        else -> AslEmptyState(
            icon = "terminal",
            title = stringResource(R.string.editor_bottom_empty),
            subtitle = stringResource(R.string.editor_bottom_terminal_future),
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BuildTab(
    console: BuildConsoleState,
    onJumpToBuildProblem: (BuildProblem) -> Unit,
    modifier: Modifier,
) {
    val colors = AslTheme.colors
    val isEmpty = console.status == BuildStatus.Idle && console.taskGroups.isEmpty() && console.logs.isEmpty()
    if (isEmpty) {
        AslEmptyState(
            icon = "hammer",
            title = stringResource(R.string.editor_build_empty),
            subtitle = stringResource(R.string.editor_build_empty_hint),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val clipboard = LocalClipboardManager.current
    val artifactLabel = stringResource(R.string.editor_build_artifact)
    val artifactSigningLabel = console.artifact?.signed?.let {
        stringResource(if (it) R.string.editor_build_signed else R.string.editor_build_unsigned)
    }
    val problemsLabel = pluralStringResource(R.plurals.editor_build_problems, console.problems.size, console.problems.size)
    val tasksLabel = stringResource(R.string.editor_build_tasks)
    val outputLabel = stringResource(R.string.editor_build_output)
    val clipboardText = console.toClipboardText(console.statusLabel(), problemsLabel, tasksLabel, outputLabel)
    Column(modifier = modifier.fillMaxSize().padding(vertical = 6.dp)) {
        BuildStatusHeader(
            console = console,
            onCopyAll = { clipboard.setText(AnnotatedString(clipboardText)) },
        )


        LazyColumn(modifier = Modifier.fillMaxSize()) {
            buildArtifactSection(console.artifact, artifactLabel, artifactSigningLabel, colors)
            buildProblemsSection(console.problems, problemsLabel, onJumpToBuildProblem)
            buildTasksSection(console.taskGroups, tasksLabel)
            buildOutputSection(console.logs, outputLabel, colors)
        }
    }
}

private fun LazyListScope.buildArtifactSection(
    artifact: BuildArtifact?,
    artifactLabel: String,
    artifactSigningLabel: String?,
    colors: AslColorScheme,
) {
    artifact?.let { artifact ->
        item { SectionLabel(artifactLabel) }
        item {
            val details = buildList {
                add(artifact.kind.name)
                artifact.sizeBytes?.let { add(formatBytes(it)) }
                artifactSigningLabel?.let { add(it) }
                artifact.sha256?.let { add("SHA-256 ${it.take(12)}…") }
            }.joinToString(" · ")
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(artifact.name, style = AslCode.codeSmall, color = colors.textPrimary)
                if (details.isNotBlank()) {
                    Text(details, style = AslCode.codeTiny, color = colors.textTertiary)
                }
            }
        }
    }
}

private fun LazyListScope.buildProblemsSection(
    problems: List<BuildProblem>,
    problemsLabel: String,
    onJumpToBuildProblem: (BuildProblem) -> Unit,
) {
    if (problems.isNotEmpty()) {
        item { SectionLabel(problemsLabel) }
        items(problems) { problem -> ProblemRow(problem, onJumpToBuildProblem) }
    }
}

private fun LazyListScope.buildTasksSection(
    taskGroups: List<BuildTaskGroup>,
    tasksLabel: String,
) {
    if (taskGroups.isNotEmpty()) {
        item { SectionLabel(tasksLabel) }
        taskGroups.forEach { group ->
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
}

private fun LazyListScope.buildOutputSection(
    logs: List<BuildLogLine>,
    outputLabel: String,
    colors: AslColorScheme,
) {
    if (logs.isNotEmpty()) {
        item { SectionLabel(outputLabel) }
        item {
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    logs.forEach { line ->
                        Text(
                            text = line.text,
                            style = AslCode.codeTiny,
                            color = if (line.isError) colors.error else colors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildStatusHeader(
    console: BuildConsoleState,
    onCopyAll: () -> Unit,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val (label, tint) = when (console.status) {
                BuildStatus.Running -> console.statusLabel() to colors.info
                BuildStatus.Succeeded -> console.statusLabel() to colors.success
                BuildStatus.Failed -> console.statusLabel() to colors.error
                BuildStatus.Cancelled -> console.statusLabel() to colors.textTertiary
                BuildStatus.Idle -> console.statusLabel() to colors.textTertiary
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
                Text(text = formatSeconds(it), style = AslCode.codeTiny, color = colors.textTertiary)
            }
            val hasContent = console.problems.isNotEmpty() || console.taskGroups.isNotEmpty() || console.logs.isNotEmpty()
            if (hasContent) {
                AslIconButton(
                    icon = "copy",
                    contentDescription = stringResource(R.string.editor_build_copy_output),
                    onClick = onCopyAll,
                    size = 32.dp,
                    iconSize = 16.dp,
                )
            }
        }
        if (console.isRunning) {
            AslLinearProgress(
                label = null,
                detail = if (console.taskCount > 0) {
                    pluralStringResource(
                        R.plurals.editor_build_task_progress,
                        console.taskCount,
                        console.finishedTaskCount,
                        console.taskCount,
                    )
                } else null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun BuildConsoleState.statusLabel(): String = when (status) {
    BuildStatus.Running -> progressMessage ?: stringResource(R.string.editor_build_running)
    BuildStatus.Succeeded -> stringResource(R.string.editor_build_successful)
    BuildStatus.Failed -> stringResource(R.string.editor_build_failed)
    BuildStatus.Cancelled -> stringResource(R.string.editor_build_cancelled)
    BuildStatus.Idle -> stringResource(R.string.editor_build_ready)
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
