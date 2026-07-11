package com.example.androidstudiolite.feature.editor.variants
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.feature.editor.variants.VariantsUiState

/** Build-variant selection is session-local UI state, not persisted domain data — no repository. */
class VariantsViewModel : BaseViewModel<VariantsUiState, Nothing>(
    initialState = VariantsUiState(),
), VariantsInteractionListener {

    override fun onSelectVariant(variant: String) {
        updateState { copy(selectedVariant = variant) }
    }
}
