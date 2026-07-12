package com.ahmadkharfan.androidstudiolite.feature.settings.editor

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class EditorSettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<EditorSettingsUiState, Nothing>(
    initialState = EditorSettingsUiState(),
), EditorSettingsInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState {
                    copy(
                        fontSize = prefs.editorFontSize,
                        colorSchemeId = prefs.editorThemeId,
                        tabSize = prefs.editorTabSize,
                        autoSave = prefs.editorAutoSave,
                        kotlinLsp = prefs.kotlinLspEnabled,
                        javaLsp = prefs.javaLspEnabled,
                        xmlLsp = prefs.xmlLspEnabled,
                    )
                }
            },
        )
    }

    override fun onFontSizeChanged(size: Int) {
        viewModelScope.launch { preferencesRepository.setEditorFontSize(size) }
    }

    override fun onColorSchemeChanged(id: String) {
        viewModelScope.launch { preferencesRepository.setEditorTheme(id) }
    }

    override fun onTabSizeChanged(size: Int) {
        viewModelScope.launch { preferencesRepository.update { it.copy(editorTabSize = size) } }
    }

    override fun onToggleAutoSave(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(editorAutoSave = enabled) } }
    }

    override fun onToggleKotlinLsp(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(kotlinLspEnabled = enabled) } }
    }

    override fun onToggleJavaLsp(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(javaLspEnabled = enabled) } }
    }

    override fun onToggleXmlLsp(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(xmlLspEnabled = enabled) } }
    }
}
