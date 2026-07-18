package com.ahmadkharfan.androidstudiolite.feature.settings.general

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.locale.AppLocale
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class GeneralViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val appContext: Context,
) : BaseViewModel<GeneralUiState, GeneralEffect>(
    initialState = GeneralUiState(),
), GeneralInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState {
                    copy(
                        themeMode = prefs.themeMode,
                        accentId = prefs.accentId,
                        language = prefs.language,
                        autoOpenLastProject = prefs.autoOpenLastProject,
                        snowfallEasterEgg = prefs.snowfallEasterEgg,
                    )
                }
            },
        )
    }

    override fun onThemeModeChanged(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    override fun onAccentChanged(id: String) {
        viewModelScope.launch { preferencesRepository.setAccent(id) }
    }

    override fun onLanguageChanged(language: String) {
        viewModelScope.launch {
            val normalized = AppLocale.supported(language)
            preferencesRepository.setLanguage(normalized)
            AppLocale.writeLanguage(appContext, normalized)
            emitEffect(GeneralEffect.RecreateForLocale)
        }
    }

    override fun onToggleAutoOpenLastProject(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoOpenLastProject(enabled) }
    }

    override fun onToggleSnowfallEasterEgg(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSnowfallEasterEgg(enabled) }
    }
}
