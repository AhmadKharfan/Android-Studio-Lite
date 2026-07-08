package com.example.androidstudiolite.core.designsystem.component.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** PermissionCard.jsx — onboarding permission card: icon, title, why-needed copy, Grant button / Granted state. */
@Composable
fun AslPermissionCard(
    title: String,
    reason: String,
    modifier: Modifier = Modifier,
    icon: String = "folder-lock",
    granted: Boolean = false,
    onGrant: () -> Unit = {},
) {
    val colors = AslTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(if (granted) colors.successContainer else colors.surfaceContainerLow, AslShape.md),
            contentAlignment = Alignment.Center,
        ) {
            AslIcon(name = if (granted) "check" else icon, size = 22.dp, tint = if (granted) colors.success else colors.textSecondary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (granted) {
                Text(text = "Granted", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.success)
            } else {
                AslButton(label = "Grant access", onClick = onGrant)
            }
        }
    }
}
