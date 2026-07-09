package com.example.androidstudiolite.designsystem.component.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.component.inputs.AslChip
import com.example.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.modifier.pressScale
import com.example.androidstudiolite.designsystem.theme.AslMotion
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

/** TemplateCard.jsx — new-project wizard template tile; accent border + check badge when selected. */
@Composable
fun AslTemplateCard(
    name: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: String = "smartphone",
    chips: List<String> = emptyList(),
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = AslTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = AslMotion.standardSpec(),
        label = "templateBorderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.accentPrimary else colors.borderDefault,
        animationSpec = AslMotion.standardSpec(),
        label = "templateBorderColor",
    )
    Box(
        modifier = modifier
            .pressScale(interactionSource)
            .clip(AslShape.lg)
            .background(colors.surface, AslShape.lg)
            .border(borderWidth, borderColor, AslShape.lg)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(colors.bgSunken, AslShape.md)
                    .border(1.dp, colors.borderSubtle, AslShape.md),
                contentAlignment = Alignment.Center,
            ) {
                AslIcon(name = icon, size = 30.dp, tint = colors.textTertiary)
            }
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { chip ->
                        AslChip(label = chip, kind = AslChipKind.Status)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = selected,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = scaleIn(AslMotion.emphasizedSpec(), initialScale = 0.4f) + fadeIn(AslMotion.enterSpec()),
            exit = scaleOut(AslMotion.exitSpec(), targetScale = 0.4f) + fadeOut(AslMotion.exitSpec()),
        ) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(20.dp)
                    .background(colors.accentPrimary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AslIcon(name = "check", size = 13.dp, tint = colors.accentOnPrimary)
            }
        }
    }
}
