package com.ahmadkharfan.androidstudiolite.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun TerminalTabRow(
    tabs: List<TerminalTab>,
    activeTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = AslTheme.colors
    if (tabs.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgElevated)
            .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            tabs.forEach { tab ->
                TerminalTabChip(
                    tab = tab,
                    active = tab.id == activeTabId,
                    canClose = tabs.size > 1,
                    compact = compact,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                    colors = colors,
                )
            }
        }
        AslIconButton(
            icon = "plus",
            contentDescription = "New terminal tab",
            onClick = onNewTab,
        )
    }
    HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
}

@Composable
private fun TerminalTabChip(
    tab: TerminalTab,
    active: Boolean,
    canClose: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    colors: AslColorScheme,
) {
    Row(
        modifier = Modifier
            .height(if (compact) 28.dp else 32.dp)
            .background(if (active) colors.surfaceContainerHigh else colors.bgElevated, AslShape.sm)
            .border(1.dp, if (active) colors.borderStrong else colors.borderDefault, AslShape.sm)
            .clickable(onClick = onSelect)
            .padding(start = if (compact) 8.dp else 10.dp, end = if (canClose) 4.dp else if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (tab.running) tab.title else "${tab.title} (exited)",
            style = if (compact) AslCode.codeTiny else AslCode.codeSmall,
            color = if (active) colors.textPrimary else colors.textSecondary,
        )
        if (canClose) {
            Box(
                modifier = Modifier
                    .size(if (compact) 18.dp else 20.dp)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "×", style = AslCode.codeSmall, color = colors.textTertiary)
            }
        }
    }
}
