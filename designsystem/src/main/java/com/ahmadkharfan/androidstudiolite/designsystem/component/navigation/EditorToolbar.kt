package com.ahmadkharfan.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenu
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** EditorToolbar.jsx — compact 48dp editor toolbar: tool-window trigger, project name, actions, accent Run pill, overflow. */
@Composable
fun AslEditorToolbar(
    modifier: Modifier = Modifier,
    projectName: String = "MyApplication",
    running: Boolean = false,
    onRun: () -> Unit = {},
    onMenu: () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit)? = null,
    overflowItems: List<AslOverflowMenuEntry> = emptyList(),
    onOverflowSelect: (AslOverflowMenuEntry.Item, Int) -> Unit = { _, _ -> },
) {
    val colors = AslTheme.colors
    Column(modifier = modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIconButton(icon = "menu", contentDescription = "Tool windows", onClick = onMenu)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AslIcon(name = "smartphone", size = 16.dp, tint = colors.textTertiary)
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions?.invoke(this)
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(36.dp)
                    .background(if (running) colors.error else colors.accentPrimary, AslShape.full)
                    .clickable(onClick = onRun)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AslIcon(
                    name = if (running) "square" else "play",
                    size = 15.dp,
                    tint = if (running) Color.White else colors.accentOnPrimary,
                )
                Text(
                    text = if (running) "Stop" else "Run",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (running) Color.White else colors.accentOnPrimary,
                )
            }
            if (overflowItems.isNotEmpty()) {
                AslOverflowMenu(items = overflowItems, onSelect = onOverflowSelect)
            }
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
