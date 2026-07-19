package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/**
 * Renders a subset of markdown (headings, lists, task lists, quotes, fenced code, bold/italic/code/links)
 * using the app's design tokens. No external markdown library.
 */
@Composable
fun AslMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onCopyCode: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val colors = AslTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        3 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    }
                    InlineMarkdown(text = block.text, color = colors.textPrimary, style = style)
                }
                is MdBlock.Paragraph -> InlineMarkdown(
                    text = block.text,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainerLow, AslShape.md)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 36.dp)
                                .background(colors.accentPrimary, RoundedCornerShape(2.dp))
                                .align(Alignment.CenterVertically),
                        )
                        InlineMarkdown(
                            text = block.text,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MdBlock.Code -> AslChatCodeBlock(
                    code = block.code,
                    language = block.language,
                    onCopy = { onCopyCode(block.code) },
                    onApply = {},
                )
                is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            when {
                                item.checked != null -> AslIcon(
                                    name = if (item.checked) "check" else "square",
                                    size = 16.dp,
                                    tint = if (item.checked) colors.success else colors.textTertiary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                block.ordered -> Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                else -> Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                            InlineMarkdown(
                                text = item.text,
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                MdBlock.HorizontalRule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )
            }
        }
    }
}

@Composable
private fun InlineMarkdown(
    text: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = AslTheme.colors
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color) {
        buildInlineAnnotatedString(text, color, colors.accentPrimary, colors.bgSunken)
    }
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                runCatching { uriHandler.openUri(it.item) }
            }
        },
    )
}

internal fun buildInlineAnnotatedString(
    source: String,
    textColor: Color,
    linkColor: Color,
    codeBg: Color,
) = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("**", i) -> {
                val end = source.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(source.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(source[i])
                    i++
                }
            }
            source.startsWith("*", i) && !source.startsWith("**", i) -> {
                val end = source.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(source.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(source[i])
                    i++
                }
            }
            source.startsWith("`", i) -> {
                val end = source.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = codeBg,
                            color = textColor,
                        ),
                    ) {
                        append(source.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(source[i])
                    i++
                }
            }
            source.startsWith("[", i) -> {
                val close = source.indexOf(']', i + 1)
                val openParen = if (close >= 0 && close + 1 < source.length && source[close + 1] == '(') {
                    close + 1
                } else {
                    -1
                }
                val closeParen = if (openParen >= 0) source.indexOf(')', openParen + 1) else -1
                if (close > i && openParen == close + 1 && closeParen > openParen) {
                    val label = source.substring(i + 1, close)
                    val url = source.substring(openParen + 1, closeParen)
                    pushStringAnnotation("URL", url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    pop()
                    i = closeParen + 1
                } else {
                    append(source[i])
                    i++
                }
            }
            else -> {
                append(source[i])
                i++
            }
        }
    }
}
