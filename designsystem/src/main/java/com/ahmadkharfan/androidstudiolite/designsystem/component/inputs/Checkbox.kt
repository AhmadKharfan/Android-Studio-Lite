package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun AslCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    indeterminate: Boolean = false,
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors
    val on = checked || indeterminate
    val boxColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.surfaceContainerHigh
            on -> colors.accentPrimary
            else -> colors.bgElevated
        },
        animationSpec = AslMotion.standardSpec(),
        label = "checkboxFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.borderDefault
            on -> colors.accentPrimary
            else -> colors.borderStrong
        },
        animationSpec = AslMotion.standardSpec(),
        label = "checkboxBorder",
    )

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .toggleable(
                value = checked,
                enabled = !disabled,
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .indication(interactionSource, ripple(bounded = false, radius = 18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(boxColor, RoundedCornerShape(4.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CheckMark(
                    visible = on,
                    indeterminate = indeterminate,
                    tint = if (disabled) colors.textDisabled else colors.accentOnPrimary,
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (disabled) colors.textDisabled else colors.textPrimary,
            )
        }
    }
}

@Composable
private fun CheckMark(
    visible: Boolean,
    indeterminate: Boolean,
    tint: androidx.compose.ui.graphics.Color,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(AslMotion.emphasizedSpec(), initialScale = 0.4f) + fadeIn(AslMotion.enterSpec(AslMotion.fast)),
        exit = scaleOut(AslMotion.exitSpec(), targetScale = 0.4f) + fadeOut(AslMotion.exitSpec()),
    ) {
        AslIcon(
            name = if (indeterminate) "minus" else "check",
            size = 13.dp,
            tint = tint,
        )
    }
}
