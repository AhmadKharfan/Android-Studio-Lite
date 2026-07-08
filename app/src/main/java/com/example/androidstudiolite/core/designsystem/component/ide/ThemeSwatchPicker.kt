package com.example.androidstudiolite.core.designsystem.component.ide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

data class AslThemeSwatch(val id: String, val name: String, val colors: List<Color>)

/** ThemeSwatchPicker.jsx — 40dp color circles (solid or multi-color pie), accent ring + check when selected. */
@Composable
fun AslThemeSwatchPicker(
    swatches: List<AslThemeSwatch>,
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = AslTheme.colors
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            swatches.forEach { swatch ->
                val selected = swatch.id == value
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(2.dp, if (selected) colors.accentPrimary else Color.Transparent, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onValueChange(swatch.id) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SwatchCircle(swatch.colors)
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(colors.surface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            AslIcon(name = "check", size = 12.dp, tint = colors.accentPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchCircle(swatchColors: List<Color>) {
    if (swatchColors.size <= 1) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(swatchColors.firstOrNull() ?: Color.Gray, CircleShape),
        )
    } else {
        Canvas(modifier = Modifier.size(34.dp)) {
            val sweep = 360f / swatchColors.size
            swatchColors.forEachIndexed { index, color ->
                drawArc(
                    color = color,
                    startAngle = index * sweep,
                    sweepAngle = sweep,
                    useCenter = true,
                )
            }
        }
    }
}
