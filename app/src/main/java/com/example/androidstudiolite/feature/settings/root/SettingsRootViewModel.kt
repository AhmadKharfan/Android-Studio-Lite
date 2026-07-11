package com.example.androidstudiolite.feature.settings.root

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.PreferencesRepository

class SettingsRootViewModel(
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<SettingsRootUiState, Nothing>(
    initialState = SettingsRootUiState(),
), SettingsRootInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs -> updateState { copy(shareUsageStats = prefs.shareUsageStats) } },
        )
    }

    override fun onQueryChanged(query: String) {
        updateState { copy(query = query) }
    }
}
