package com.ahmadkharfan.androidstudiolite.designsystem.component.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslChipKind { Filter, Input, Assist, Status }
enum class AslChipStatus { Neutral, Success, Error, Warning, Info }

/** Chip.jsx — full-radius pill, 32dp. filter (selectable) | input (removable) | assist (icon action) | status (colored). */
@Composable
fun AslChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: AslChipKind = AslChipKind.Filter,
    selected: Boolean = false,
    disabled: Boolean = false,
    icon: String? = null,
    status: AslChipStatus = AslChipStatus.Neutral,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AslTheme.colors
    val (targetBg, targetFg, targetBorder) = when {
        kind == AslChipKind.Status -> statusColors(status, colors)
        disabled -> Triple(colors.surfaceContainerLow, colors.textDisabled, colors.borderSubtle)
        kind == AslChipKind.Filter && selected -> Triple(colors.accentPrimaryContainer, colors.accentPrimary, Color.Transparent)
        else -> Triple(Color.Transparent, colors.textPrimary, colors.borderStrong)
    }
    val bg by animateColorAsState(targetBg, AslMotion.standardSpec(), label = "chipBg")
    val fg by animateColorAsState(targetFg, AslMotion.standardSpec(), label = "chipFg")
    val border by animateColorAsState(targetBorder, AslMotion.standardSpec(), label = "chipBorder")
    val interactive = !disabled && (onClick != null || kind == AslChipKind.Filter)

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(AslShape.full)
            .background(bg, AslShape.full)
            .border(1.dp, border, AslShape.full)
            .then(if (interactive && onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (kind == AslChipKind.Filter && selected) {
            AslIcon(name = "check", size = 14.dp, tint = fg)
        } else if (icon != null) {
            AslIcon(name = icon, size = 14.dp, tint = fg)
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
        if (kind == AslChipKind.Input && onRemove != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onRemove),
            ) {
                AslIcon(name = "x", size = 14.dp, tint = fg)
            }
        }
    }
}

private fun statusColors(
    status: AslChipStatus,
    colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme,
): Triple<Color, Color, Color> {
    return when (status) {
        AslChipStatus.Neutral -> Triple(colors.surfaceContainerLow, colors.textSecondary, colors.borderDefault)
        AslChipStatus.Success -> Triple(colors.successContainer, colors.success, Color.Transparent)
        AslChipStatus.Error -> Triple(colors.errorContainer, colors.error, Color.Transparent)
        AslChipStatus.Warning -> Triple(colors.warningContainer, colors.warning, Color.Transparent)
        AslChipStatus.Info -> Triple(colors.infoContainer, colors.info, Color.Transparent)
    }
}
