package com.ahmadkharfan.androidstudiolite.designsystem.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun Modifier.aslCard(shape: Shape = AslShape.lg): Modifier {
    val colors = AslTheme.colors
    return this
        .background(colors.surface, shape)
        .border(1.dp, colors.borderDefault, shape)
}
