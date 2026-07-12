package com.ahmadkharfan.androidstudiolite.designsystem.component.feedback

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/** CircularProgress.jsx — small inline spinner, 20dp default. */
@Composable
fun AslCircularProgress(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    thickness: Dp = 2.5.dp,
    color: Color = AslTheme.colors.accentPrimary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = AslTheme.colors.surfaceContainerHigh,
        strokeWidth = thickness,
    )
}
