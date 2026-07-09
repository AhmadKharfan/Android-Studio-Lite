package com.example.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslElevation
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.designsystem.theme.aslBordered

/** FindBar.jsx — floats over the editor's top edge: query field, match counter, prev/next/close. */
@Composable
fun AslFindBar(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    matchCount: Int = 0,
    currentMatch: Int = 0,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = AslTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .shadow(AslElevation.overlay, AslShape.md)
            .background(colors.surface, AslShape.md)
            .aslBordered(AslShape.md)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AslIcon(name = "search", size = 16.dp, tint = colors.textTertiary)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            textStyle = AslCode.codeSmall.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentPrimary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = "Find in file", style = AslCode.codeSmall, color = colors.textTertiary)
                }
                inner()
            },
        )
        Text(
            text = when {
                query.isEmpty() -> ""
                matchCount == 0 -> "No results"
                else -> "$currentMatch/$matchCount"
            },
            style = AslCode.codeTiny,
            color = if (query.isNotEmpty() && matchCount == 0) colors.error else colors.textTertiary,
        )
        AslIconButton(icon = "chevron-up", contentDescription = "Previous match", onClick = onPrev, size = 32.dp, iconSize = 16.dp, disabled = matchCount == 0)
        AslIconButton(icon = "chevron-down", contentDescription = "Next match", onClick = onNext, size = 32.dp, iconSize = 16.dp, disabled = matchCount == 0)
        AslIconButton(icon = "x", contentDescription = "Close find bar", onClick = onClose, size = 32.dp, iconSize = 16.dp)
    }
}
