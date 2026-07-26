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
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

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
    val annotated = remember(text, color) {
        buildInlineAnnotatedString(text, color, colors.accentPrimary, colors.bgSunken)
    }
    BasicText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
    )
}

internal fun buildInlineAnnotatedString(
    source: String,
    textColor: Color,
    linkColor: Color,
    codeBg: Color,
) = buildAnnotatedString {
    InlineMarkdownRenderer(
        source = source,
        output = this,
        styles = InlineMarkdownStyles(textColor, linkColor, codeBg),
    ).appendAll()
}

private class InlineMarkdownRenderer(
    private val source: String,
    private val output: AnnotatedString.Builder,
    private val styles: InlineMarkdownStyles,
) {
    private var index = 0

    fun appendAll() {
        while (index < source.length) appendNext()
    }

    private fun appendNext() {
        when {
            source.startsWith("**", index) -> appendStyled("**", styles.bold)
            source.startsWith("*", index) -> appendStyled("*", styles.italic)
            source.startsWith("`", index) -> appendStyled("`", styles.code)
            source.startsWith("[", index) -> appendLink()
            else -> appendLiteral()
        }
    }

    private fun appendStyled(marker: String, style: SpanStyle) {
        val end = source.indexOf(marker, index + marker.length)
        if (end <= index) {
            appendLiteral()
            return
        }
        output.withStyle(style) { append(source.substring(index + marker.length, end)) }
        index = end + marker.length
    }

    private fun appendLink() {
        val link = parseLink(index)
        if (link == null) {
            appendLiteral()
            return
        }
        output.withLink(LinkAnnotation.Url(link.url)) {
            withStyle(styles.link) { append(link.label) }
        }
        index = link.nextIndex
    }

    private fun appendLiteral() {
        output.append(source[index])
        index++
    }

    private fun parseLink(start: Int): InlineLink? {
        val labelEnd = source.indexOf(']', start + 1)
        val urlStart = labelEnd + 2
        if (labelEnd <= start || urlStart > source.lastIndex || source[urlStart - 1] != '(') return null
        val urlEnd = source.indexOf(')', urlStart)
        if (urlEnd < urlStart) return null
        return InlineLink(
            label = source.substring(start + 1, labelEnd),
            url = source.substring(urlStart, urlEnd),
            nextIndex = urlEnd + 1,
        )
    }
}

private class InlineMarkdownStyles(textColor: Color, linkColor: Color, codeBackground: Color) {
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = textColor)
    val italic = SpanStyle(fontStyle = FontStyle.Italic, color = textColor)
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        background = codeBackground,
        color = textColor,
    )
    val link = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
}

private data class InlineLink(val label: String, val url: String, val nextIndex: Int)
