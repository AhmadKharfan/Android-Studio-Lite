package com.ahmadkharfan.androidstudiolite.designsystem.component.buttons
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuItem

@Immutable
sealed interface AslOverflowMenuEntry {
    @Immutable
    data class Item(
        val label: String,
        val icon: String? = null,
        val shortcut: String? = null,
        val disabled: Boolean = false,
        val destructive: Boolean = false,
    ) : AslOverflowMenuEntry

    data object Divider : AslOverflowMenuEntry
}

@Composable
fun AslOverflowMenu(
    items: List<AslOverflowMenuEntry>,
    onSelect: (AslOverflowMenuEntry.Item, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val latestOnSelect by rememberUpdatedState(onSelect)
    val toggleOpen = remember { { open = !open } }
    val dismiss = remember { { open = false } }

    Box(modifier = modifier) {
        AslIconButton(
            icon = "more-vertical",
            contentDescription = "More options",
            active = open,
            onClick = toggleOpen,
        )
        if (open) {
            AslDropdownMenu(
                expanded = true,
                onDismissRequest = dismiss,
            ) {
                OverflowMenuEntries(
                    items = items,
                    onDismiss = dismiss,
                    onSelect = { item, index -> latestOnSelect(item, index) },
                )
            }
        }
    }
}

@Composable
private fun OverflowMenuEntries(
    items: List<AslOverflowMenuEntry>,
    onDismiss: () -> Unit,
    onSelect: (AslOverflowMenuEntry.Item, Int) -> Unit,
) {
    val latestOnSelect by rememberUpdatedState(onSelect)
    val dismissMenu = remember(onDismiss) { onDismiss }
    items.forEachIndexed { index, entry ->
        key(
            when (entry) {
                is AslOverflowMenuEntry.Item -> entry.label
                AslOverflowMenuEntry.Divider -> "divider-$index"
            },
        ) {
            when (entry) {
                is AslOverflowMenuEntry.Divider -> AslDropdownMenuDivider()
                is AslOverflowMenuEntry.Item -> {
                    AslDropdownMenuItem(
                        label = entry.label,
                        icon = entry.icon,
                        shortcut = entry.shortcut,
                        destructive = entry.destructive,
                        enabled = !entry.disabled,
                        onClick = {
                            dismissMenu()
                            latestOnSelect(entry, index)
                        },
                    )
                }
            }
        }
    }
}
