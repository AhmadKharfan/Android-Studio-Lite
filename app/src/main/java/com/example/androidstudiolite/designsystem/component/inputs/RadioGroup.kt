package com.example.androidstudiolite.designsystem.component.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import com.example.androidstudiolite.designsystem.theme.AslMotion
import com.example.androidstudiolite.designsystem.theme.AslTheme

data class AslRadioOption(
    val label: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean = false,
)

/** RadioGroup.jsx — vertical list of 40dp rows, optional per-option description. */
@Composable
fun AslRadioGroup(
    options: List<AslRadioOption>,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors

    Column(modifier = modifier.selectableGroup()) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        options.forEach { option ->
            val selected = option.value == value
            val rowDisabled = disabled || option.disabled
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .selectable(
                        selected = selected,
                        enabled = !rowDisabled,
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onValueChange(option.value) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioDot(
                    selected = selected,
                    disabled = rowDisabled,
                    interactionSource = interactionSource,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (rowDisabled) colors.textDisabled else colors.textPrimary,
                    )
                    if (option.description != null) {
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rowDisabled) colors.textDisabled else colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioDot(
    selected: Boolean,
    disabled: Boolean,
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val borderColor by animateColorAsState(
        targetValue = when {
            disabled -> colors.borderDefault
            selected -> colors.accentPrimary
            else -> colors.borderStrong
        },
        animationSpec = AslMotion.standardSpec(),
        label = "radioBorderColor",
    )
    // The inner ring thickness grows from a hairline outline to a filled centre when selected.
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 5.5.dp else 1.5.dp,
        animationSpec = AslMotion.standardSpec(),
        label = "radioRing",
    )
    // A circular-clipped area carries the ripple so it renders as a clean circle, never a square.
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .indication(interactionSource, ripple(bounded = false, radius = 18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.bgElevated, CircleShape)
                .border(ringWidth, borderColor, CircleShape),
        )
    }
}
