package com.example.androidstudiolite.designsystem.component.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslTheme

data class AslSplitButtonItem(val label: String, val icon: String? = null)

/** SplitButton.jsx — accent primary action + chevron opening a task menu (Run ▾ assembleDebug…). */
@Composable
fun AslSplitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "play",
    items: List<AslSplitButtonItem> = emptyList(),
    onSelect: (AslSplitButtonItem, Int) -> Unit = { _, _ -> },
    disabled: Boolean = false,
) {
    val colors = AslTheme.colors
    var open by remember { mutableStateOf(false) }
    val containerColor = if (disabled) colors.surfaceContainerHigh else colors.accentPrimary
    val contentColor = if (disabled) colors.textDisabled else colors.accentOnPrimary

    Box(modifier = modifier) {
        Row(modifier = Modifier.height(40.dp)) {
            Row(
                modifier = Modifier
                    .background(containerColor, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .clickable(enabled = !disabled, onClick = onClick)
                    .padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AslIcon(name = icon, size = 18.dp, tint = contentColor)
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
            Row(
                modifier = Modifier
                    .background(containerColor, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .border(
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (disabled) colors.borderDefault else Color.Black.copy(alpha = 0.18f),
                        ),
                        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    )
                    .clickable(enabled = !disabled) { open = !open }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AslIcon(name = if (open) "chevron-up" else "chevron-down", size = 16.dp, tint = contentColor)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    leadingIcon = item.icon?.let { iconName -> { AslIcon(name = iconName, size = 16.dp, tint = colors.textSecondary) } },
                    onClick = {
                        open = false
                        onSelect(item, index)
                    },
                )
            }
        }
    }
}
