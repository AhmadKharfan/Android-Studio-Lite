package com.example.androidstudiolite.feature.uidesigner
import androidx.lifecycle.ViewModel
import com.example.androidstudiolite.feature.uidesigner.DesignerInteraction
import com.example.androidstudiolite.feature.uidesigner.DesignerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DesignerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DesignerUiState())
    val uiState: StateFlow<DesignerUiState> = _uiState.asStateFlow()

    fun onInteraction(interaction: DesignerInteraction) {
        when (interaction) {
            is DesignerInteraction.TabSelected -> _uiState.update { it.copy(activeTab = interaction.tab) }
            is DesignerInteraction.IdChanged -> _uiState.update { it.copy(properties = it.properties.copy(id = interaction.value)) }
            is DesignerInteraction.TextChanged -> _uiState.update { it.copy(properties = it.properties.copy(text = interaction.value)) }
            is DesignerInteraction.LayoutWidthChanged -> _uiState.update { it.copy(properties = it.properties.copy(layoutWidth = interaction.value)) }
            is DesignerInteraction.LayoutHeightChanged -> _uiState.update { it.copy(properties = it.properties.copy(layoutHeight = interaction.value)) }
        }
    }
}
