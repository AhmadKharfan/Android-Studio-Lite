package com.example.androidstudiolite.feature.editor.variants
import androidx.lifecycle.ViewModel
import com.example.androidstudiolite.feature.editor.variants.VariantsInteraction
import com.example.androidstudiolite.feature.editor.variants.VariantsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Build-variant selection is session-local UI state, not persisted domain data — no repository. */
class VariantsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VariantsUiState())
    val uiState: StateFlow<VariantsUiState> = _uiState.asStateFlow()

    fun onInteraction(interaction: VariantsInteraction) {
        when (interaction) {
            is VariantsInteraction.SelectVariant -> _uiState.value = _uiState.value.copy(selectedVariant = interaction.variant)
        }
    }
}
