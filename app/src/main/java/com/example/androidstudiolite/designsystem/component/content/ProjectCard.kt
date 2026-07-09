package com.example.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.modifier.pressScale
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

private val languageColors = mapOf(
    "Kotlin" to Color(0xFF7F52FF),
    "Java" to Color(0xFFB07219),
    "C++" to Color(0xFFF34B7D),
    "Compose" to Color(0xFF4285F4),
)

/** ProjectCard.jsx — hub project card: initial tile, name + language badge, mono path, last-opened. */
@Composable
fun AslProjectCard(
    name: String,
    path: String,
    modifier: Modifier = Modifier,
    lastOpened: String? = null,
    language: String = "Kotlin",
    onClick: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
) {
    val colors = AslTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interactionSource)
            .clip(AslShape.lg)
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.accentPrimaryContainer, AslShape.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = colors.accentPrimary,
                fontWeight = FontWeight.SemiBold,
                style = AslCode.codeBody.copy(fontSize = 17.sp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    modifier = Modifier
                        .background(colors.surfaceContainerLow, AslShape.xs)
                        .border(1.dp, colors.borderSubtle, AslShape.xs)
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(languageColors[language] ?: colors.textTertiary, CircleShape),
                    )
                    Text(text = language, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
            }
            Text(
                text = path,
                style = AslCode.codeTiny,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (lastOpened != null) {
                Text(
                    text = "Opened $lastOpened",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (onMenu != null) {
            AslIconButton(icon = "more-vertical", contentDescription = "Project options", onClick = onMenu)
        }
    }
}
