package com.example.androidstudiolite.core.designsystem.component.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslMotion
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
        // A real drag gesture on a generously-sized handle bar, not just a tap on a 4dp-tall pill:
        // drag up past the threshold to expand, drag down past it to collapse. Tap still toggles too.
        val density = LocalDensity.current
        val toggleThresholdPx = with(density) { 28.dp.toPx() }
        val dragAccumulator = remember { mutableFloatStateOf(0f) }
        val expandedState = rememberUpdatedState(expanded)
        val onToggleState = rememberUpdatedState(onToggle)
        val draggableState = rememberDraggableState { delta -> dragAccumulator.floatValue += delta }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragAccumulator.floatValue = 0f },
                    onDragStopped = {
                        val dragged = dragAccumulator.floatValue
                        if (!expandedState.value && dragged < -toggleThresholdPx) {
                            onToggleState.value()
                        } else if (expandedState.value && dragged > toggleThresholdPx) {
                            onToggleState.value()
                        }
                        dragAccumulator.floatValue = 0f
                    },
                )
                .padding(top = 12.dp, bottom = 12.dp),
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
                val tabBg by animateColorAsState(
                    targetValue = if (active) colors.accentPrimaryContainer else Color.Transparent,
                    animationSpec = AslMotion.standardSpec(),
                    label = "bottomTabBg",
                )
                val tabFg by animateColorAsState(
                    targetValue = if (active) colors.accentPrimary else colors.textSecondary,
                    animationSpec = AslMotion.standardSpec(),
                    label = "bottomTabFg",
                )
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .background(tabBg, AslShape.sm)
                        .clickable { onSelect(tab.id) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (tab.icon != null) {
                        AslIcon(name = tab.icon, size = 14.dp, tint = tabFg)
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = tabFg,
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
        // The docked panel body slides open/closed instead of appearing/vanishing instantly.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
            exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
        ) {
            Column {
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
}
