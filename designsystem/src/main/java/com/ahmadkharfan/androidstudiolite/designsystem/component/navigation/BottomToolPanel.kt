package com.ahmadkharfan.androidstudiolite.designsystem.component.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import kotlin.math.min

data class AslBottomPanelTab(
    val id: String,
    val label: String,
    val icon: String? = null,
    val count: Int? = null,
    val error: Boolean = false,
)

private val PanelOpenGestureThreshold = 14.dp
private val PanelCollapseThreshold = 72.dp
private val PanelFreeZoneMargin = 36.dp

@Composable
fun AslBottomToolPanel(
    tabs: List<AslBottomPanelTab>,
    activeId: String?,
    modifier: Modifier = Modifier,
    contentHeight: Dp = 0.dp,
    defaultContentHeight: Dp = 260.dp,
    onContentHeightChange: (Dp) -> Unit = {},
    onSelect: (String) -> Unit = {},
    onToggle: () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val colors = AslTheme.colors
    val density = LocalDensity.current
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    var dragging by remember { mutableStateOf(false) }
    var dragHeightPx by remember { mutableFloatStateOf(0f) }
    var dragStartHeightPx by remember { mutableFloatStateOf(0f) }
    var upwardDragPx by remember { mutableFloatStateOf(0f) }
    val contentHeightState = rememberUpdatedState(contentHeight)
    val onHeightChangeState = rememberUpdatedState(onContentHeightChange)
    val onToggleState = rememberUpdatedState(onToggle)
    val defaultHeightState = rememberUpdatedState(defaultContentHeight)
    val maxHeightState = rememberUpdatedState(maxContentHeight)

    LaunchedEffect(contentHeight) {
        if (!dragging) {
            dragHeightPx = with(density) { contentHeight.toPx() }
        }
    }

    fun settleAndCommit() {
        val settledPx = settleBottomPanelHeightPx(
            currentHeightPx = dragHeightPx,
            dragStartHeightPx = dragStartHeightPx,
            upwardDragPx = upwardDragPx,
            defaultHeightPx = with(density) { defaultHeightState.value.toPx() },
            maxHeightPx = with(density) { maxHeightState.value.toPx() },
            collapseThresholdPx = with(density) { PanelCollapseThreshold.toPx() },
            openGestureThresholdPx = with(density) { PanelOpenGestureThreshold.toPx() },
            freeZoneMarginPx = with(density) { PanelFreeZoneMargin.toPx() },
        )
        dragHeightPx = settledPx
        onHeightChangeState.value(with(density) { settledPx.toDp() })
    }

    val settleSpring = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val visualTarget = if (dragging) with(density) { dragHeightPx.toDp() } else contentHeight
    val animatedHeight by animateDpAsState(
        targetValue = visualTarget,
        animationSpec = if (dragging) tween(durationMillis = AslMotion.instant) else settleSpring,
        label = "bottomPanelHeight",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgElevated),
    ) {
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(contentHeight, defaultContentHeight, maxContentHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        upwardDragPx = 0f
                        dragStartHeightPx = with(density) { contentHeightState.value.toPx() }
                        dragHeightPx = dragStartHeightPx

                        val dragAfterSlop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            dragging = true
                            change.consume()
                        }

                        if (dragAfterSlop == null) {
                            onToggleState.value()
                            return@awaitEachGesture
                        }

                        dragging = true
                        val maxPx = with(density) { maxHeightState.value.toPx() }
                        try {
                            drag(dragAfterSlop.id) { change ->
                                val deltaPx = change.previousPosition.y - change.position.y
                                if (deltaPx > 0f) {
                                    upwardDragPx += deltaPx
                                }
                                dragHeightPx = (dragHeightPx + deltaPx).coerceIn(0f, maxPx)
                                change.consume()
                            }
                        } finally {
                            dragging = false
                            settleAndCommit()
                        }
                    }
                }
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
        if (animatedHeight > 0.dp) {
            Column {
                HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedHeight),
                ) {
                    content()
                }
            }
        }
    }
}

private fun settleBottomPanelHeightPx(
    currentHeightPx: Float,
    dragStartHeightPx: Float,
    upwardDragPx: Float,
    defaultHeightPx: Float,
    maxHeightPx: Float,
    collapseThresholdPx: Float,
    openGestureThresholdPx: Float,
    freeZoneMarginPx: Float,
): Float {
    val freeThresholdPx = defaultHeightPx + freeZoneMarginPx
    val collapseThreshold = min(collapseThresholdPx, defaultHeightPx * 0.28f)

    if (dragStartHeightPx <= 0f && upwardDragPx >= openGestureThresholdPx) {
        return defaultHeightPx
    }

    if (currentHeightPx <= collapseThreshold) {
        return 0f
    }

    if (currentHeightPx >= freeThresholdPx) {
        return currentHeightPx.coerceAtMost(maxHeightPx)
    }

    return defaultHeightPx
}
