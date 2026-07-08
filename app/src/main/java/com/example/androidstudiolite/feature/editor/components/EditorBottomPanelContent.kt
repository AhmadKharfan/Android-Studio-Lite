package com.example.androidstudiolite.feature.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.content.AslEmptyState
import com.example.androidstudiolite.core.designsystem.component.content.AslLogLine
import com.example.androidstudiolite.core.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.core.designsystem.component.ide.AslBuildOutputLine
import com.example.androidstudiolite.core.designsystem.component.ide.AslTaskStatus
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.editor.uiState.AppLogLineUiModel
import com.example.androidstudiolite.feature.editor.uiState.BuildOutputLineUiModel

@Composable
fun EditorBottomPanelContent(
    activeTabId: String,
    running: Boolean,
    buildProgressPercent: Int?,
    buildLines: List<BuildOutputLineUiModel>,
    modifier: Modifier = Modifier,
    appLogLines: List<AppLogLineUiModel> = emptyList(),
    onJumpToTab: (String) -> Unit = {},
) {
    val colors = AslTheme.colors
    when (activeTabId) {
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
            subtitle = "Terminal and diagnostics are coming in a future update.",
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
