package com.example.androidstudiolite.core.designsystem.component.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** Slider.jsx — slider with a label + live value readout (e.g. editor font size). */
@Composable
fun AslSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    unit: String = "",
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors

    Column(modifier = modifier) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (disabled) colors.textDisabled else colors.textSecondary,
                )
                Text(
                    text = "${value.toInt()}$unit",
                    style = AslTheme.code.codeSmall,
                    color = if (disabled) colors.textDisabled else colors.textPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            enabled = !disabled,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentPrimary,
                activeTrackColor = colors.accentPrimary,
                inactiveTrackColor = colors.surfaceContainerHigh,
                disabledThumbColor = colors.borderStrong,
                disabledActiveTrackColor = colors.borderStrong,
                disabledInactiveTrackColor = colors.surfaceContainerHigh,
            ),
        )
    }
}
