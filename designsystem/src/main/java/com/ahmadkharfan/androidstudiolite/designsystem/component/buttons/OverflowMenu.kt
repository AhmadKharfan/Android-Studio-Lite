package com.ahmadkharfan.androidstudiolite.designsystem.component.buttons
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuItem

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
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AslIconButton(
            icon = "more-vertical",
            contentDescription = "More options",
            active = open,
            onClick = { open = !open },
        )
        AslDropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            items.forEachIndexed { index, entry ->
                when (entry) {
                    is AslOverflowMenuEntry.Divider -> AslDropdownMenuDivider()
                    is AslOverflowMenuEntry.Item -> AslDropdownMenuItem(
                        label = entry.label,
                        icon = entry.icon,
                        shortcut = entry.shortcut,
                        destructive = entry.destructive,
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
