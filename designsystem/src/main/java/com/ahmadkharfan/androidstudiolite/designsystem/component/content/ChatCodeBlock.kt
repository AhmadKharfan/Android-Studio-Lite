package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** ChatCodeBlock.jsx — code block embedded in an AI chat reply, with Copy + Apply actions. */
@Composable
fun AslChatCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String = "kotlin",
    applied: Boolean = false,
    onCopy: () -> Unit = {},
    onApply: () -> Unit = {},
) {
    val colors = AslTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AslShape.md)
            .background(colors.editorCanvas, AslShape.md)
            .border(1.dp, colors.borderDefault, AslShape.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(colors.surfaceContainerLow)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = language, style = AslCode.codeTiny, color = colors.textTertiary, modifier = Modifier.weight(1f))
            ChatCodeButton(icon = "copy", label = "Copy", tint = colors.textSecondary, onClick = onCopy)
            ChatCodeButton(
                icon = if (applied) "check" else "circle-play",
                label = if (applied) "Applied" else "Apply",
                tint = colors.accentPrimary,
                onClick = onApply,
            )
        }
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        Text(
            text = code,
            style = AslCode.codeSmall,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ChatCodeButton(icon: String, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(AslShape.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AslIcon(name = icon, size = 14.dp, tint = tint)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
