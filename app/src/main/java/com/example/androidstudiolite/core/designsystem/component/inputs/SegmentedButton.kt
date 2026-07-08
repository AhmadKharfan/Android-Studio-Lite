package com.example.androidstudiolite.core.designsystem.component.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

data class AslSegmentedOption(val label: String, val value: String, val icon: String? = null)

/** SegmentedButton.jsx — connected segmented button, single-select, 40dp (e.g. Light/Dark/System). */
@Composable
fun AslSegmentedButton(
    options: List<AslSegmentedOption>,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
    fullWidth: Boolean = false,
) {
    val colors = AslTheme.colors

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .height(40.dp)
            .border(1.dp, colors.borderStrong, AslShape.md)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(colors.borderStrong),
                )
            }
            val selected = option.value == value
            val fg = when {
                disabled -> colors.textDisabled
                selected -> colors.accentPrimary
                else -> colors.textSecondary
            }
            Row(
                modifier = Modifier
                    .then(if (fullWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (!disabled && selected) colors.accentPrimaryContainer else Color.Transparent)
                    .selectable(
                        selected = selected,
                        enabled = !disabled,
                        role = Role.RadioButton,
                        onClick = { onValueChange(option.value) },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (selected) {
                    AslIcon(name = "check", size = 15.dp, tint = fg)
                    Spacer(Modifier.width(6.dp))
                } else if (option.icon != null) {
                    AslIcon(name = option.icon, size = 16.dp, tint = fg)
                    Spacer(Modifier.width(6.dp))
                }
                Text(text = option.label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}
