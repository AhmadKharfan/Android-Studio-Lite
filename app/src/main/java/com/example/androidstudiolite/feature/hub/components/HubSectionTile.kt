package com.example.androidstudiolite.feature.hub.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.modifier.pressScale
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme

/** The Hub's "More" grid tile — icon over label, screen-local (not a shared design-system component). */
@Composable
fun HubSectionTile(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interactionSource)
            .clip(AslShape.md)
            .background(colors.surface, AslShape.md)
            .border(1.dp, colors.borderDefault, AslShape.md)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AslIcon(name = icon, size = 20.dp, tint = colors.textSecondary)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun HubSectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = com.example.androidstudiolite.core.designsystem.theme.AslLetterSpacing.overline,
        color = colors.textTertiary,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}
