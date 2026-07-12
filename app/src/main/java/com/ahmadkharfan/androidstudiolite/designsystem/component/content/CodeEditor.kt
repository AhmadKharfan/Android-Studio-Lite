package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

enum class AslSyntaxColor { Keyword, StringLiteral, Comment, Function, Type, Variable, Number }
enum class AslLineGit { Added, Modified, Deleted }

data class AslCodeSpan(val text: String, val color: AslSyntaxColor? = null)
data class AslCodeLine(
    val spans: List<AslCodeSpan>,
    val git: AslLineGit? = null,
    val breakpoint: Boolean = false,
    val active: Boolean = false,
)

/** CodeEditor.jsx — static editor chrome mock: gutter (line №, git bars, breakpoints) + syntax-colored content. */
@Composable
fun AslCodeEditor(
    lines: List<AslCodeLine>,
    modifier: Modifier = Modifier,
    startLine: Int = 1,
) {
    val colors = AslTheme.colors
    Row(modifier = modifier.background(colors.editorCanvas).height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .background(colors.editorGutter)
                .padding(vertical = 10.dp),
        ) {
            lines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier
                        .height(20.dp)
                        .padding(start = 6.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (line.breakpoint) colors.error else Color.Transparent, CircleShape),
                    )
                    Text(
                        text = "${startLine + index}",
                        style = AslCode.codeSmall,
                        color = if (line.active) colors.textSecondary else colors.textTertiary,
                        modifier = Modifier.width(30.dp).padding(start = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(line.git?.let { gitColor(it, colors) } ?: Color.Transparent),
                    )
                }
            }
        }
        VerticalDivider(color = colors.borderSubtle, thickness = 1.dp)
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .height(20.dp)
                        .background(if (line.active) colors.editorLineHighlight else Color.Transparent)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = buildLineText(line, colors), style = AslCode.codeBody)
                }
            }
        }
    }
}

@Composable
private fun buildLineText(line: AslCodeLine, colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme) =
    buildAnnotatedString {
        line.spans.forEach { span ->
            val color = when (span.color) {
                AslSyntaxColor.Keyword -> colors.syntaxKeyword
                AslSyntaxColor.StringLiteral -> colors.syntaxString
                AslSyntaxColor.Comment -> colors.syntaxComment
                AslSyntaxColor.Function -> colors.syntaxFunction
                AslSyntaxColor.Type -> colors.syntaxType
                AslSyntaxColor.Variable -> colors.syntaxVariable
                AslSyntaxColor.Number -> colors.syntaxNumber
                null -> colors.syntaxVariable
            }
            withStyle(
                SpanStyle(
                    color = color,
                    fontStyle = if (span.color == AslSyntaxColor.Comment) FontStyle.Italic else FontStyle.Normal,
                ),
            ) {
                append(span.text)
            }
        }
    }

private fun gitColor(git: AslLineGit, colors: com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme): Color = when (git) {
    AslLineGit.Added -> colors.success
    AslLineGit.Modified -> colors.info
    AslLineGit.Deleted -> colors.error
}
