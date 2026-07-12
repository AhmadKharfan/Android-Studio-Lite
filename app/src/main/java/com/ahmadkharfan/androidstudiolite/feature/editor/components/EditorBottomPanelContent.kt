package com.ahmadkharfan.androidstudiolite.feature.editor.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLogLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslBuildOutputLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslTaskStatus
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.AppLogLineUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.BuildOutputLineUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.DiagnosticUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity

@Composable
fun EditorBottomPanelContent(
    activeTabId: String,
    running: Boolean,
    buildProgressPercent: Int?,
    buildLines: List<BuildOutputLineUiModel>,
    modifier: Modifier = Modifier,
    appLogLines: List<AppLogLineUiModel> = emptyList(),
    diagnostics: List<DiagnosticUiModel> = emptyList(),
    activeFileName: String? = null,
    onJumpToTab: (String) -> Unit = {},
    onJumpToDiagnostic: (DiagnosticUiModel) -> Unit = {},
) {
    val colors = AslTheme.colors
    when (activeTabId) {
        "diag" -> Column(modifier = modifier.fillMaxSize()) {
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
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
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
        "build" -> Column(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
            if (buildLines.isEmpty()) {
                AslEmptyState(icon = "hammer", title = "No build output yet", subtitle = "Run the project to see Gradle task output here.")
            } else {
                if (running && buildProgressPercent != null) {
                    AslLinearProgress(
                        value = buildProgressPercent.toFloat(),
                        label = "assembleDebug",
                        detail = "$buildProgressPercent%",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                buildLines.forEach { line ->
                    AslBuildOutputLine(
                        text = line.text,
                        depth = line.depth,
                        status = line.status?.toTaskStatus(),
                        duration = line.duration,
                    )
                    if (line.jumpToTabId != null) {
                        Text(
                            text = line.jumpToTabId,
                            style = AslCode.codeTiny,
                            color = colors.info,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToTab(line.jumpToTabId) }
                                .padding(start = (30 + line.depth * 18).dp, end = 12.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
        "logs" -> Column(modifier = modifier.fillMaxSize().padding(vertical = 6.dp)) {
            if (appLogLines.isEmpty()) {
                AslEmptyState(icon = "scroll-text", title = "No app logs yet", subtitle = "Run the project to see logcat output here.")
            } else {
                appLogLines.forEach { line ->
                    AslLogLine(time = line.time, level = line.level, tag = line.tag, message = line.message)
                }
            }
        }
        else -> AslEmptyState(
            icon = "terminal",
            title = "Nothing here yet",
            subtitle = "The terminal is coming in a future update.",
            modifier = modifier.fillMaxSize(),
        )
    }
}

private fun String.toTaskStatus(): AslTaskStatus? = when (this) {
    "success" -> AslTaskStatus.Success
    "failed" -> AslTaskStatus.Failed
    "running" -> AslTaskStatus.Running
    "skipped" -> AslTaskStatus.Skipped
    else -> null
}
