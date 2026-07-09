package com.example.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme

/** BreadcrumbBar.jsx — compact 28dp path strip under the file tabs: app > src > main > MainActivity.kt */
@Composable
fun AslBreadcrumbBar(
    segments: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    val colors = AslTheme.colors
    androidx.compose.foundation.layout.Column(modifier = modifier.background(colors.bgElevated)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { index, segment ->
                val last = index == segments.lastIndex
                if (index > 0) {
                    AslIcon(name = "chevron-right", size = 12.dp, tint = colors.textTertiary)
                }
                Text(
                    text = segment,
                    style = AslCode.codeTiny,
                    fontWeight = if (last) FontWeight.Medium else FontWeight.Normal,
                    color = if (last) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier
                        .clip(AslShape.xs)
                        .then(if (!last) Modifier.clickable { onSelect(index) } else Modifier)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
    }
}
