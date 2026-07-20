package com.ahmadkharfan.androidstudiolite.feature.settings.general

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.model.EditorColorScheme
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class GeneralViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val appContext: Context,
) : BaseViewModel<GeneralUiState, Nothing>(
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
                        autoOpenLastProject = prefs.autoOpenLastProject,
                    )
                }
            },
        )
    }

    override fun onThemeModeChanged(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            preferencesRepository.setEditorTheme(
                EditorColorScheme.defaultId(resolveDarkUi(mode)),
            )
        }
    }

    override fun onAccentChanged(id: String) {
        viewModelScope.launch { preferencesRepository.setAccent(id) }
    }

    override fun onToggleAutoOpenLastProject(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoOpenLastProject(enabled) }
    }

    private fun resolveDarkUi(mode: AppThemeMode): Boolean = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> {
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }
}
