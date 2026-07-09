package com.example.androidstudiolite.feature.settings.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorSettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorSettingsUiState())
    val uiState: StateFlow<EditorSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.observePreferences().collect { prefs ->
                _uiState.value = EditorSettingsUiState(
                    fontSize = prefs.editorFontSize,
                    colorSchemeId = prefs.editorThemeId,
                    tabSize = prefs.editorTabSize,
                    autoSave = prefs.editorAutoSave,
                    kotlinLsp = prefs.kotlinLspEnabled,
                    javaLsp = prefs.javaLspEnabled,
                    xmlLsp = prefs.xmlLspEnabled,
                )
            }
        }
    }

    fun onInteraction(interaction: EditorSettingsInteraction) {
        viewModelScope.launch {
            when (interaction) {
                is EditorSettingsInteraction.FontSizeChanged -> preferencesRepository.setEditorFontSize(interaction.size)
                is EditorSettingsInteraction.ColorSchemeChanged -> preferencesRepository.setEditorTheme(interaction.id)
                is EditorSettingsInteraction.TabSizeChanged -> preferencesRepository.update { it.copy(editorTabSize = interaction.size) }
                is EditorSettingsInteraction.ToggleAutoSave -> preferencesRepository.update { it.copy(editorAutoSave = interaction.enabled) }
                is EditorSettingsInteraction.ToggleKotlinLsp -> preferencesRepository.update { it.copy(kotlinLspEnabled = interaction.enabled) }
                is EditorSettingsInteraction.ToggleJavaLsp -> preferencesRepository.update { it.copy(javaLspEnabled = interaction.enabled) }
                is EditorSettingsInteraction.ToggleXmlLsp -> preferencesRepository.update { it.copy(xmlLspEnabled = interaction.enabled) }
            }
        }
    }
}
