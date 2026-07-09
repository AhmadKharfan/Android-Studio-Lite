package com.example.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

data class AslNavRailItem(val id: String, val icon: String, val label: String)

/** NavRail.jsx — compact 72dp navigation rail, icon + label (sub-navigation inside Git/AI panels). */
@Composable
fun AslNavRail(
    items: List<AslNavRailItem>,
    activeId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(colors.bgElevated)
                .padding(vertical = 8.dp),
        ) {
            items.forEach { item ->
                val active = item.id == activeId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 28.dp)
                            .background(if (active) colors.accentPrimaryContainer else androidx.compose.ui.graphics.Color.Transparent, AslShape.full),
                        contentAlignment = Alignment.Center,
                    ) {
                        AslIcon(name = item.icon, size = 20.dp, tint = if (active) colors.accentPrimary else colors.textSecondary)
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) colors.textPrimary else colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        VerticalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
