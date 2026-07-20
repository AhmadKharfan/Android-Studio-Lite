package com.ahmadkharfan.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun AslDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AslTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 196.dp),
        offset = offset,
        containerColor = colors.surface,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, colors.borderStrong),
        shape = AslShape.lg,
        properties = PopupProperties(focusable = true),
        content = content,
    )
}

@Composable
fun AslDropdownMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    shortcut: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = AslTheme.colors
    val textColor = when {
        !enabled -> colors.textDisabled
        destructive -> colors.error
        else -> colors.textPrimary
    }
    val iconTint = when {
        !enabled -> colors.textDisabled
        destructive -> colors.error
        else -> colors.textSecondary
    }
    DropdownMenuItem(
        modifier = modifier,
        text = {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
        },
        leadingIcon = icon?.let { iconName ->
            { AslIcon(name = iconName, size = 16.dp, tint = iconTint) }
        },
        trailingIcon = shortcut?.let { sc ->
            { Text(text = sc, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary) }
        } ?: trailingIcon,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun AslDropdownMenuDivider() {
    HorizontalDivider()
}
