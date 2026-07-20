package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AslElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 1.dp
    val overlay: Dp = 6.dp
    val modal: Dp = 12.dp
}

@Composable
fun Modifier.aslBordered(shape: Shape, color: androidx.compose.ui.graphics.Color = LocalAslColors.current.borderDefault): Modifier =
    this.border(width = 1.dp, color = color, shape = shape)
