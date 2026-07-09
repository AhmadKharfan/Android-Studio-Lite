package com.example.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslTheme

/** EmptyState.jsx — centered empty state: circled icon, title, subtitle, primary (+secondary) CTA. */
@Composable
fun AslEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: String = "folder-open",
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    val colors = AslTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.surfaceContainerLow, CircleShape)
                .border(1.dp, colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AslIcon(name = icon, size = 32.dp, tint = colors.textTertiary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (actionLabel != null || secondaryLabel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                if (actionLabel != null) {
                    AslButton(label = actionLabel, variant = AslButtonVariant.Primary, onClick = onAction)
                }
                if (secondaryLabel != null) {
                    AslButton(label = secondaryLabel, variant = AslButtonVariant.Secondary, onClick = onSecondary)
                }
            }
        }
    }
}
