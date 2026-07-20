package com.ahmadkharfan.androidstudiolite.feature.hub.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslLetterSpacing
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

@Composable
fun HubSectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = AslTheme.colors
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = AslLetterSpacing.overline,
        color = colors.textTertiary,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}
