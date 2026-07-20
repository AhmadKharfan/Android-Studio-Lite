package com.ahmadkharfan.androidstudiolite.designsystem.component.ide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslCircularProgress
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslTaskStatus { Success, Failed, Running, Skipped }

@Composable
fun AslBuildOutputLine(
    text: String,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    status: AslTaskStatus? = null,
    duration: String? = null,
) {
    val colors = AslTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp)
            .padding(start = (12 + depth * 18).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.size(13.dp)) {
            when (status) {
                AslTaskStatus.Success -> AslIcon(name = "check", size = 13.dp, tint = colors.success)
                AslTaskStatus.Failed -> AslIcon(name = "x", size = 13.dp, tint = colors.error)
                AslTaskStatus.Running -> AslCircularProgress(size = 13.dp, thickness = 2.dp, color = colors.info)
                AslTaskStatus.Skipped -> AslIcon(name = "minus", size = 13.dp, tint = colors.textTertiary)
                null -> {}
            }
        }
        Text(
            text = text,
            style = AslCode.codeSmall,
            color = when (status) {
                AslTaskStatus.Failed -> colors.error
                AslTaskStatus.Skipped -> colors.textTertiary
                else -> colors.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (duration != null) {
            Text(text = duration, style = AslCode.codeTiny, color = colors.textTertiary)
        }
    }
}
