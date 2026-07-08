package com.example.androidstudiolite.core.designsystem.component.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

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
                    modifier = Modifier.indication(
                        interactionSource,
                        ripple(bounded = false, radius = 20.dp),
                    ),
                )
                Spacer(Modifier.width(12.dp))
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
private fun RadioDot(selected: Boolean, disabled: Boolean, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    val borderColor = when {
        disabled -> colors.borderDefault
        selected -> colors.accentPrimary
        else -> colors.borderStrong
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(18.dp)
            .width(18.dp)
            .background(colors.bgElevated, CircleShape)
            .border(if (selected) 5.5.dp else 1.5.dp, borderColor, CircleShape),
    )
}
