package com.ahmadkharfan.androidstudiolite.designsystem.component.buttons

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslIconButtonVariant { Ghost, Filled, Outlined }

@Composable
fun AslIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AslIconButtonVariant = AslIconButtonVariant.Ghost,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    disabled: Boolean = false,
    badge: String? = null,
) {
    val colors = AslTheme.colors
    val containerColor by animateColorAsState(
        targetValue = when {
            disabled -> Color.Transparent
            variant == AslIconButtonVariant.Filled -> colors.accentPrimary
            active -> colors.accentPrimaryContainer
            else -> Color.Transparent
        },
        animationSpec = AslMotion.standardSpec(),
        label = "iconButtonContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.textDisabled
            variant == AslIconButtonVariant.Filled -> colors.accentOnPrimary
            active -> colors.accentPrimary
            else -> colors.textSecondary
        },
        animationSpec = AslMotion.standardSpec(),
        label = "iconButtonContent",
    )
    val border = if (variant == AslIconButtonVariant.Outlined && !disabled) {
        BorderStroke(1.dp, colors.borderStrong)
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(AslShape.md)
            .background(containerColor, AslShape.md)
            .then(if (border != null) Modifier.border(border, AslShape.md) else Modifier)
            .clickable(enabled = !disabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        AslIcon(name = icon, size = iconSize, tint = contentColor)
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .sizeIn(minWidth = 14.dp, minHeight = 14.dp)
                    .background(colors.error, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White,
                )
            }
        }
    }
}
