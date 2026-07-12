package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** tokens/elevation.css — "borders over shadows": panels/rails stay flat, cards get a light
 *  tonal lift, only menus/popups/dialogs get real shadow depth. Every elevated surface still
 *  gets a 1dp [AslColorScheme.borderDefault] stroke — see [Modifier.aslBordered]. */
object AslElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 1.dp
    val overlay: Dp = 6.dp
    val modal: Dp = 12.dp
}

/** Applies the 1dp border every elevated IDE surface carries, per the design's elevation rule. */
@Composable
fun Modifier.aslBordered(shape: Shape, color: androidx.compose.ui.graphics.Color = LocalAslColors.current.borderDefault): Modifier =
    this.border(width = 1.dp, color = color, shape = shape)
