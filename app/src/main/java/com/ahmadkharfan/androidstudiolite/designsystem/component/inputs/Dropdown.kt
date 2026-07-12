package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

data class AslDropdownOption(val label: String, val value: String)

/** Dropdown.jsx — exposed dropdown menu: 40dp field opening a compact option popup. */
@Composable
fun AslDropdown(
    options: List<AslDropdownOption>,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select…",
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == value }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (disabled) colors.textDisabled else colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
        }
        androidx.compose.foundation.layout.Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AslMetrics.fieldHeight)
                    .background(if (disabled) colors.surfaceContainerLow else colors.bgElevated, AslShape.md)
                    .border(if (open) 2.dp else 1.dp, if (disabled) colors.borderDefault else if (open) colors.accentPrimary else colors.borderStrong, AslShape.md)
                    .clickable(enabled = !disabled) { open = !open }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = current?.label ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        disabled -> colors.textDisabled
                        current != null -> colors.textPrimary
                        else -> colors.textTertiary
                    },
                    modifier = Modifier.weight(1f),
                )
                AslIcon(name = if (open) "chevron-up" else "chevron-down", size = 16.dp, tint = colors.textTertiary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    val selected = option.value == value
                    DropdownMenuItem(
                        text = { Text(option.label, color = colors.textPrimary) },
                        trailingIcon = if (selected) {
                            { AslIcon(name = "check", size = 16.dp, tint = colors.accentPrimary) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            onValueChange(option.value)
                        },
                    )
                }
            }
        }
    }
}
