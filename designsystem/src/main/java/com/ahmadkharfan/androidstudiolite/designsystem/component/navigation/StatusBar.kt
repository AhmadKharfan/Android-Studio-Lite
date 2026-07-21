package com.ahmadkharfan.androidstudiolite.designsystem.component.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslStatusTone { Neutral, Success, Error, Warning }

sealed interface AslStatusBarEntry {
    data class Item(
        val label: String,
        val icon: String? = null,
        val tone: AslStatusTone = AslStatusTone.Neutral,
        val spin: Boolean = false,
        val onClick: (() -> Unit)? = null,
    ) : AslStatusBarEntry
    data object Spacer : AslStatusBarEntry
}

@Composable
fun AslStatusBar(
    items: List<AslStatusBarEntry>,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    Column(modifier = modifier.background(colors.bgElevated)) {
        HorizontalDivider(color = colors.borderDefault, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AslMetrics.statusBar)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { entry ->
                when (entry) {
                    is AslStatusBarEntry.Spacer -> Box(modifier = Modifier.weight(1f))
                    is AslStatusBarEntry.Item -> {
                        val tint = when (entry.tone) {
                            AslStatusTone.Success -> colors.success
                            AslStatusTone.Error -> colors.error
                            AslStatusTone.Warning -> colors.warning
                            AslStatusTone.Neutral -> colors.textTertiary
                        }
                        Row(
                            modifier = Modifier
                                .height(22.dp)
                                .clip(AslShape.xs)
                                .then(if (entry.onClick != null) Modifier.clickable(onClick = entry.onClick) else Modifier)
                                .padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (entry.icon != null) {
                                if (entry.spin) {
                                    val transition = rememberInfiniteTransition(label = "statusBarSpin")
                                    val angle by transition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                                        label = "statusBarSpinAngle",
                                    )
                                    AslIcon(name = entry.icon, size = 13.dp, tint = tint, modifier = Modifier.rotate(angle))
                                } else {
                                    AslIcon(name = entry.icon, size = 13.dp, tint = tint)
                                }
                            }
                            androidx.compose.material3.Text(text = entry.label, style = AslCode.codeTiny, color = tint)
                        }
                    }
                }
            }
        }
    }
}
