package com.ahmadkharfan.androidstudiolite.feature.editor.variants
import androidx.compose.runtime.Immutable

@Immutable
data class VariantsUiState(
    val module: String = "app",
    val selectedVariant: String = "debug",
)
