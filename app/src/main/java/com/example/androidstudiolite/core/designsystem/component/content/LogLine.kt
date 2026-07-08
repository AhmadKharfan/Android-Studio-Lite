package com.example.androidstudiolite.core.designsystem.component.content

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslLogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/** LogLine.jsx — single logcat-style row: time, [LEVEL], optional tag, message. */
@Composable
fun AslLogLine(
    time: String,
    message: String,
    modifier: Modifier = Modifier,
    level: AslLogLevel = AslLogLevel.DEBUG,
    tag: String? = null,
) {
    val colors = AslTheme.colors
    val levelColor = when (level) {
        AslLogLevel.VERBOSE, AslLogLevel.DEBUG -> colors.textTertiary
        AslLogLevel.INFO -> colors.info
        AslLogLevel.WARN -> colors.warning
        AslLogLevel.ERROR -> colors.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 18.dp)
            .padding(horizontal = 12.dp, vertical = 1.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = time, style = AslCode.codeTiny, color = colors.textTertiary)
        Text(
            text = "[${level.name}]",
            style = AslCode.codeTiny,
            color = levelColor,
            fontWeight = if (level == AslLogLevel.WARN || level == AslLogLevel.ERROR) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(44.dp),
        )
        if (tag != null) {
            Text(text = "$tag:", style = AslCode.codeTiny, color = colors.textSecondary)
        }
        Text(
            text = message,
            style = AslCode.codeTiny,
            color = if (level == AslLogLevel.ERROR) colors.error else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
