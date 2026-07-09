package com.example.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslTheme

/** LinearProgress.jsx — 4dp track, determinate (0-100) or indeterminate (null value). */
@Composable
fun AslLinearProgress(
    modifier: Modifier = Modifier,
    value: Float? = null,
    label: String? = null,
    detail: String? = null,
) {
    val colors = AslTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null || detail != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (label != null) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                }
                if (detail != null) {
                    Text(text = detail, style = AslCode.codeTiny, color = colors.textTertiary)
                }
            }
        }
        if (value == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.accentPrimary,
                trackColor = colors.surfaceContainerHigh,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { value / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.accentPrimary,
                trackColor = colors.surfaceContainerHigh,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
    }
}
