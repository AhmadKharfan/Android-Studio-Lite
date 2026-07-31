package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** Divider defaulting to the design system's border token rather than Material's outline. */
@Composable
fun AslHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = AslTheme.colors.borderSubtle,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

@Composable
fun AslVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = AslTheme.colors.borderSubtle,
) {
    VerticalDivider(modifier = modifier, thickness = thickness, color = color)
}
