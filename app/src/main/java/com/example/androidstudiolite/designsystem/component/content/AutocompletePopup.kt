package com.example.androidstudiolite.designsystem.component.content
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslElevation
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.designsystem.theme.aslBordered
enum class AslSuggestionKind { Method, Field, Class, Keyword, Snippet }
data class AslSuggestion(
    val label: String,
    val detail: String? = null,
    val type: String? = null,
    val kind: AslSuggestionKind = AslSuggestionKind.Snippet,
)
@Composable
fun AslAutocompletePopup(
    suggestions: List<AslSuggestion>,
    modifier: Modifier = Modifier,
    activeIndex: Int = 0,
    onSelect: (AslSuggestion, Int) -> Unit = { _, _ -> },
) {
    val colors = AslTheme.colors
    Column(
        modifier = modifier
            .sizeIn(minWidth = 260.dp, maxWidth = 420.dp)
            .shadow(AslElevation.overlay, AslShape.md)
            .background(colors.surface, AslShape.md)
            .aslBordered(AslShape.md)
            .padding(4.dp),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            val active = index == activeIndex
            val scrollState = rememberScrollState()
            val (icon, tint) = when (suggestion.kind) {
                AslSuggestionKind.Method -> "square-function" to colors.syntaxFunction
                AslSuggestionKind.Field -> "box" to colors.info
                AslSuggestionKind.Class -> "shapes" to colors.syntaxType
                AslSuggestionKind.Keyword -> "key-round" to colors.syntaxKeyword
                AslSuggestionKind.Snippet -> "braces" to colors.textTertiary
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(AslShape.sm)
                    .background(if (active) colors.accentPrimaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(suggestion, index) }
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AslIcon(name = icon, size = 15.dp, tint = tint)
                Text(
                    text = suggestion.label,
                    style = AslCode.codeSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (suggestion.detail != null) {
                    Text(
                        text = suggestion.detail,
                        style = AslCode.codeSmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (suggestion.type != null) {
                    Text(
                        text = suggestion.type,
                        style = AslCode.codeTiny,
                        color = colors.textTertiary,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
