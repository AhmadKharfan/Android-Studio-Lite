package com.ahmadkharfan.androidstudiolite.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMetrics
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** ToolWindowPanel.jsx — 48dp header (title + actions) + scrollable content. */
@Composable
fun AslToolWindowPanel(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    actions: @Composable (RowScope.() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = AslTheme.colors
    Row(modifier = modifier.width(width).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(colors.bgElevated),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AslMetrics.panelHeader)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    actions?.invoke(this)
                    if (onClose != null) {
                        AslIconButton(icon = "x", contentDescription = "Close panel", size = 32.dp, iconSize = 16.dp, onClick = onClose)
                    }
                }
                HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            ) {
                content()
            }
        }
        VerticalDivider(color = colors.borderDefault, thickness = 1.dp)
    }
}
