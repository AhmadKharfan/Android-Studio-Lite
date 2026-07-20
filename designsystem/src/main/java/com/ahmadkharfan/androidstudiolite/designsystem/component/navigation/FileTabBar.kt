package com.ahmadkharfan.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

data class AslFileTab(val id: String, val name: String, val icon: String? = null, val modified: Boolean = false)

@Composable
fun AslFileTabBar(
    tabs: List<AslFileTab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    Column(modifier = modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AslMetrics.tabHeight)
                .horizontalScroll(rememberScrollState()),
        ) {
            tabs.forEach { tab ->
                val active = tab.id == activeId
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(if (active) colors.editorCanvas else Color.Transparent)
                        .clickable { onSelect(tab.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab.icon != null) {
                            AslIcon(
                                name = tab.icon,
                                size = 15.dp,
                                tint = if (active) colors.accentPrimary else colors.textTertiary,
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            text = tab.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) colors.textPrimary else colors.textSecondary,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
                        if (tab.modified) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(colors.accentPrimary, CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .clickable { onClose(tab.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                AslIcon(name = "x", size = 13.dp, tint = colors.textTertiary)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) colors.accentPrimary else Color.Transparent),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(colors.borderSubtle),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
