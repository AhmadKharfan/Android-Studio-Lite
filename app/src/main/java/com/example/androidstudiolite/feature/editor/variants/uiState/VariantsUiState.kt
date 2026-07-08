package com.example.androidstudiolite.feature.editor.variants.uiState

import androidx.compose.runtime.Immutable

@Immutable
data class VariantsUiState(
    val module: String = "app",
    val selectedVariant: String = "debug",
)
