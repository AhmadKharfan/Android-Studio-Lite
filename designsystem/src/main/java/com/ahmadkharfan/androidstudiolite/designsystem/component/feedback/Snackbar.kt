package com.ahmadkharfan.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslSnackbarTone { Neutral, Success, Error }

@Composable
fun AslSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    tone: AslSnackbarTone = AslSnackbarTone.Neutral,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = AslTheme.colors
    val toneColor = when (tone) {
        AslSnackbarTone.Success -> colors.success
        AslSnackbarTone.Error -> colors.error
        AslSnackbarTone.Neutral -> colors.bgBase
    }

    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .defaultMinSize(minHeight = 48.dp)
            .background(colors.textPrimary, AslShape.md)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            AslIcon(name = icon, size = 18.dp, tint = toneColor)
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.bgBase,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentPrimary,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
