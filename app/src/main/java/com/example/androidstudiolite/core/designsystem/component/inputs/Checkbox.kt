package com.example.androidstudiolite.core.designsystem.component.inputs

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** Checkbox.jsx — 18dp box inside a ≥40dp touch row. */
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
    val boxColor = when {
        disabled -> colors.surfaceContainerHigh
        on -> colors.accentPrimary
        else -> colors.bgElevated
    }
    val borderColor = when {
        disabled -> colors.borderDefault
        on -> colors.accentPrimary
        else -> colors.borderStrong
    }

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
                .size(18.dp)
                .indication(interactionSource, ripple(bounded = false, radius = 20.dp))
                .background(boxColor, RoundedCornerShape(4.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                AslIcon(
                    name = if (indeterminate) "minus" else "check",
                    size = 13.dp,
                    tint = if (disabled) colors.textDisabled else colors.accentOnPrimary,
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (disabled) colors.textDisabled else colors.textPrimary,
            )
        }
    }
}
