package com.ahmadkharfan.androidstudiolite.feature.settings.root

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel

class SettingsRootViewModel : BaseViewModel<SettingsRootUiState, Nothing>(
    initialState = SettingsRootUiState(),
), SettingsRootInteractionListener {

    override fun onQueryChanged(query: String) {
        updateState { copy(query = query) }
    }
}
