package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.modifier.pressScale
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun AslListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    iconColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
    selected: Boolean = false,
    disabled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = AslTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.accentPrimaryContainer else Color.Transparent,
        animationSpec = AslMotion.standardSpec(),
        label = "listItemBackground",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val clickableRow = onClick != null && !disabled
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (subtitle != null) 60.dp else 48.dp)

                .then(if (clickableRow) Modifier.pressScale(interactionSource) else Modifier)
                .background(background)
                .then(
                    if (clickableRow) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            role = Role.Button,
                            onClick = onClick!!,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(4.dp))
            }
            if (icon != null) {
                AslIcon(
                    name = icon,
                    size = 20.dp,
                    tint = if (disabled) colors.textDisabled else (iconColor ?: colors.textSecondary),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (disabled) colors.textDisabled else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (disabled) colors.textDisabled else colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(14.dp))
                trailing()
            }
        }
        if (divider) {
            HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        }
    }
}
