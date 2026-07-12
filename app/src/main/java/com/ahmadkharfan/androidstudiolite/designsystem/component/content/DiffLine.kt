package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslDiffKind { Added, Removed, Modified, Context }

/** DiffLine.jsx — single unified-diff row: gutter bar, two line-number columns, sign glyph, code text. */
@Composable
fun AslDiffLine(
    kind: AslDiffKind,
    text: String,
    modifier: Modifier = Modifier,
    oldNo: Int? = null,
    newNo: Int? = null,
) {
    val colors = AslTheme.colors
    val barColor = when (kind) {
        AslDiffKind.Added -> colors.success
        AslDiffKind.Removed -> colors.error
        AslDiffKind.Modified -> colors.info
        AslDiffKind.Context -> Color.Transparent
    }
    val signColor = when (kind) {
        AslDiffKind.Added -> colors.success
        AslDiffKind.Removed -> colors.error
        AslDiffKind.Modified, AslDiffKind.Context -> colors.textTertiary
    }
    val sign = when (kind) {
        AslDiffKind.Added -> "+"
        AslDiffKind.Removed -> "-"
        AslDiffKind.Modified -> "~"
        AslDiffKind.Context -> " "
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp)
            .background(if (kind == AslDiffKind.Context) Color.Transparent else barColor.copy(alpha = 0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(barColor))
        Text(
            text = oldNo?.toString().orEmpty(),
            style = AslCode.codeTiny,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
        Row(modifier = Modifier.width(34.dp)) {
            Text(
                text = newNo?.toString().orEmpty(),
                style = AslCode.codeTiny,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(colors.borderSubtle))
        }
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            Text(text = sign, style = AslCode.codeTiny, color = signColor)
        }
        Text(
            text = text,
            style = AslCode.codeSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
