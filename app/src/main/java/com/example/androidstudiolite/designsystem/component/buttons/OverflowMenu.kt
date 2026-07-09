package com.example.androidstudiolite.designsystem.component.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslTheme

sealed interface AslOverflowMenuEntry {
    data class Item(
        val label: String,
        val icon: String? = null,
        val shortcut: String? = null,
        val disabled: Boolean = false,
        val destructive: Boolean = false,
    ) : AslOverflowMenuEntry
    data object Divider : AslOverflowMenuEntry
}

/** OverflowMenu.jsx — 3-dot overflow menu with compact popup rows. */
@Composable
fun AslOverflowMenu(
    items: List<AslOverflowMenuEntry>,
    onSelect: (AslOverflowMenuEntry.Item, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AslIconButton(
            icon = "more-vertical",
            contentDescription = "More options",
            active = open,
            onClick = { open = !open },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { index, entry ->
                when (entry) {
                    is AslOverflowMenuEntry.Divider -> HorizontalDivider()
                    is AslOverflowMenuEntry.Item -> DropdownMenuItem(
                        text = {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.destructive) colors.error else colors.textPrimary,
                            )
                        },
                        leadingIcon = entry.icon?.let { iconName ->
                            {
                                AslIcon(
                                    name = iconName,
                                    size = 16.dp,
                                    tint = if (entry.destructive) colors.error else colors.textSecondary,
                                )
                            }
                        },
                        trailingIcon = entry.shortcut?.let { shortcut ->
                            { Text(text = shortcut, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary) }
                        },
                        enabled = !entry.disabled,
                        onClick = {
                            open = false
                            onSelect(entry, index)
                        },
                    )
                }
            }
        }
    }
}
