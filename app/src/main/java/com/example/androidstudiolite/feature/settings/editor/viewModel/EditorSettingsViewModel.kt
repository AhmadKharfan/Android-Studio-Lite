package com.example.androidstudiolite.feature.settings.editor.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.usecase.ObservePreferencesUseCase
import com.example.androidstudiolite.domain.usecase.UpdateEditorFontSizeUseCase
import com.example.androidstudiolite.domain.usecase.UpdateEditorThemeUseCase
import com.example.androidstudiolite.domain.usecase.UpdatePreferencesUseCase
import com.example.androidstudiolite.feature.settings.editor.interaction.EditorSettingsInteraction
import com.example.androidstudiolite.feature.settings.editor.uiState.EditorSettingsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorSettingsViewModel(
    private val observePreferences: ObservePreferencesUseCase,
    private val updateFontSize: UpdateEditorFontSizeUseCase,
    private val updateEditorTheme: UpdateEditorThemeUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorSettingsUiState())
    val uiState: StateFlow<EditorSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { prefs ->
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
                is EditorSettingsInteraction.FontSizeChanged -> updateFontSize(interaction.size)
                is EditorSettingsInteraction.ColorSchemeChanged -> updateEditorTheme(interaction.id)
                is EditorSettingsInteraction.TabSizeChanged -> updatePreferences { it.copy(editorTabSize = interaction.size) }
                is EditorSettingsInteraction.ToggleAutoSave -> updatePreferences { it.copy(editorAutoSave = interaction.enabled) }
                is EditorSettingsInteraction.ToggleKotlinLsp -> updatePreferences { it.copy(kotlinLspEnabled = interaction.enabled) }
                is EditorSettingsInteraction.ToggleJavaLsp -> updatePreferences { it.copy(javaLspEnabled = interaction.enabled) }
                is EditorSettingsInteraction.ToggleXmlLsp -> updatePreferences { it.copy(xmlLspEnabled = interaction.enabled) }
            }
        }
    }
}
