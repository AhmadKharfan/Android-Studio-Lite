package com.example.androidstudiolite.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBadge
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBadgeTone
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

sealed interface AslToolRailEntry {
    data class Item(val id: String, val icon: String, val label: String, val badge: String? = null) : AslToolRailEntry
    data object Spacer : AslToolRailEntry
    data object Divider : AslToolRailEntry
}

/** ToolRail.jsx — vertical 48dp tool-window rail. Active = 3dp accent left bar + accent container. */
@Composable
fun AslToolRail(
    items: List<AslToolRailEntry>,
    activeId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(colors.bgElevated)
                .padding(vertical = 4.dp),
        ) {
            items.forEach { entry ->
                when (entry) {
                    is AslToolRailEntry.Spacer -> Box(modifier = Modifier.weight(1f))
                    is AslToolRailEntry.Divider -> HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = colors.borderSubtle,
                    )
                    is AslToolRailEntry.Item -> {
                        val active = entry.id == activeId
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onSelect(entry.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(vertical = 10.dp)
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(if (active) colors.accentPrimary else Color.Transparent),
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(AslShape.md)
                                    .background(if (active) colors.accentPrimaryContainer else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                AslIcon(
                                    name = entry.icon,
                                    size = 20.dp,
                                    tint = if (active) colors.accentPrimary else colors.textSecondary,
                                )
                            }
                            if (entry.badge != null) {
                                AslBadge(
                                    count = entry.badge,
                                    tone = AslBadgeTone.Error,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-2).dp, y = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        VerticalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
