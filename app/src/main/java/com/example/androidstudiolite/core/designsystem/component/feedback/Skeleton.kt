package com.example.androidstudiolite.core.designsystem.component.feedback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

enum class AslSkeletonVariant { List, Editor }

/** Skeleton.jsx — pulsing placeholder: "list" (avatar+lines rows) or "editor" (gutter+code lines). */
@Composable
fun AslSkeleton(
    modifier: Modifier = Modifier,
    variant: AslSkeletonVariant = AslSkeletonVariant.List,
    rows: Int = 3,
) {
    val pulse by rememberInfiniteTransition(label = "asl-skeleton").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "asl-skeleton-alpha",
    )
    val colors = AslTheme.colors

    @Composable
    fun Block(width: Dp, height: Dp = 12.dp) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .alpha(pulse)
                .clip(AslShape.xs)
                .background(colors.surfaceContainerHigh),
        )
    }

    if (variant == AslSkeletonVariant.Editor) {
        val widths = listOf(48, 72, 60, 84, 38, 66, 52, 76)
        Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            widths.take(maxOf(rows, 4)).forEachIndexed { index, percent ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Block(width = 22.dp, height = 10.dp)
                    Block(width = (percent).dp * 2, height = 10.dp)
                }
            }
        }
    } else {
        Column(modifier = modifier) {
            repeat(rows) { index ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp)
                                .alpha(pulse)
                                .clip(AslShape.md)
                                .background(colors.surfaceContainerHigh),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Block(width = 160.dp)
                            Block(width = 100.dp, height = 9.dp)
                        }
                    }
                    HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
                }
            }
        }
    }
}
