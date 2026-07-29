package com.ahmadkharfan.androidstudiolite.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslAppTheme

@Composable
internal fun AslPreview(content: @Composable () -> Unit) {
    AslAppTheme(darkTheme = isSystemInDarkTheme(), content = content)
}

