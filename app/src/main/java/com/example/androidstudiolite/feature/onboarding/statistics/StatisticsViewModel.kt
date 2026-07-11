package com.example.androidstudiolite.feature.onboarding.statistics

import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.launch

class StatisticsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : BaseViewModel<StatisticsUiState, Nothing>(
    initialState = StatisticsUiState(),
), StatisticsInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState { copy(shareUsageStats = prefs.shareUsageStats) }
            },
        )
    }

    override fun onToggleShareUsageStats(enabled: Boolean) {
        updateState { copy(shareUsageStats = enabled) }
        viewModelScope.launch { preferencesRepository.setShareUsageStats(enabled) }
    }
}
