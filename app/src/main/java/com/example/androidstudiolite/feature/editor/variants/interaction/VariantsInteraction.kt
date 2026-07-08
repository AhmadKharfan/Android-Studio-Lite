package com.example.androidstudiolite.feature.editor.variants.interaction

sealed interface VariantsInteraction {
    data class SelectVariant(val variant: String) : VariantsInteraction
}
