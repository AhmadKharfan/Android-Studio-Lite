package com.ahmadkharfan.androidstudiolite.designsystem.component.buttons
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ahmadkharfan.androidstudiolite.designsystem.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDropdownMenuItem

@Immutable
sealed interface AslOverflowMenuEntry {
    class Item(
        val label: String,
        val icon: String? = null,
        val shortcut: String? = null,
        val disabled: Boolean = false,
        val destructive: Boolean = false,
        val onClick: () -> Unit,
    ) : AslOverflowMenuEntry

    data object Divider : AslOverflowMenuEntry
}

@Composable
fun AslOverflowMenu(
    items: List<AslOverflowMenuEntry>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val toggleOpen = remember { { open = !open } }
    val dismiss = remember { { open = false } }

    Box(modifier = modifier) {
        AslIconButton(
            icon = "more-vertical",
            contentDescription = stringResource(R.string.asl_more_options),
            active = open,
            onClick = toggleOpen,
        )
        if (open) {
            AslDropdownMenu(
                expanded = true,
                onDismissRequest = dismiss,
            ) {
                OverflowMenuEntries(items = items, onDismiss = dismiss)
            }
        }
    }
}

@Composable
private fun OverflowMenuEntries(
    items: List<AslOverflowMenuEntry>,
    onDismiss: () -> Unit,
) {
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
                            entry.onClick()
                        },
                    )
                }
            }
        }
    }
}
