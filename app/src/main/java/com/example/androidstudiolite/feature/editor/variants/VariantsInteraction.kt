package com.example.androidstudiolite.feature.editor.variants
sealed interface VariantsInteraction {
    data class SelectVariant(val variant: String) : VariantsInteraction
}
