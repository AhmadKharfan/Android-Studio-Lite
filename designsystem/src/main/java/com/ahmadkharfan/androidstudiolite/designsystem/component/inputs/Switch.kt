package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** Switch.jsx — M3-proportioned track (44x26), accent when on. Label left, control right. */
@Composable
fun AslSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors
    val trackColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.surfaceContainerHigh
            checked -> colors.accentPrimary
            else -> colors.surfaceContainerHigh
        },
        animationSpec = AslMotion.standardSpec(),
        label = "switchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked && !disabled) colors.accentPrimary else colors.borderStrong,
        animationSpec = AslMotion.standardSpec(),
        label = "switchBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.textDisabled
            checked -> colors.accentOnPrimary
            else -> colors.textTertiary
        },
        animationSpec = AslMotion.standardSpec(),
        label = "switchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(AslMotion.normal, easing = AslMotion.easeStandard),
        label = "switchThumbOffset",
    )

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .then(if (label != null) Modifier.fillMaxWidth() else Modifier)
            .toggleable(
                value = checked,
                enabled = !disabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (label != null) Arrangement.SpaceBetween else Arrangement.Start,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (disabled) colors.textDisabled else colors.textPrimary,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(CircleShape)
                .indication(interactionSource, ripple(bounded = false, radius = 24.dp))
                .background(trackColor, CircleShape)
                .border(1.dp, borderColor, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset, y = 3.dp)
                    .size(18.dp)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}
