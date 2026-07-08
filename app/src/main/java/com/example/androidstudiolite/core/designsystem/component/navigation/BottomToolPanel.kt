package com.example.androidstudiolite.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

data class AslBottomPanelTab(
    val id: String,
    val label: String,
    val icon: String? = null,
    val count: Int? = null,
    val error: Boolean = false,
)

/** BottomToolPanel.jsx — JetBrains docked panel: drag handle + tab row + content, peek/expanded. */
@Composable
fun AslBottomToolPanel(
    tabs: List<AslBottomPanelTab>,
    activeId: String?,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    height: Dp = 260.dp,
    onSelect: (String) -> Unit = {},
    onToggle: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val colors = AslTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgElevated),
    ) {
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(top = 5.dp, bottom = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(colors.borderStrong, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { tab ->
                val active = tab.id == activeId
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .background(if (active) colors.accentPrimaryContainer else Color.Transparent, AslShape.sm)
                        .clickable { onSelect(tab.id) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (tab.icon != null) {
                        AslIcon(name = tab.icon, size = 14.dp, tint = if (active) colors.accentPrimary else colors.textSecondary)
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) colors.accentPrimary else colors.textSecondary,
                    )
                    if (tab.count != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 16.dp)
                                .background(
                                    if (tab.error) colors.error else colors.surfaceContainerHigh,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${tab.count}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (tab.error) Color.White else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
        if (expanded) {
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
    }
}
