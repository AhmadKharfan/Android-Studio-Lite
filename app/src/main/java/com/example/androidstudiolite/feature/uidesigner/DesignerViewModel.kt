package com.example.androidstudiolite.feature.uidesigner
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.feature.uidesigner.DesignerUiState

class DesignerViewModel : BaseViewModel<DesignerUiState, Nothing>(
    initialState = DesignerUiState(),
), DesignerInteractionListener {

    override fun onTabSelected(tab: DesignerTab) {
        updateState { copy(activeTab = tab) }
    }

    override fun onIdChanged(value: String) {
        updateState { copy(properties = properties.copy(id = value)) }
    }

    override fun onTextChanged(value: String) {
        updateState { copy(properties = properties.copy(text = value)) }
    }

    override fun onLayoutWidthChanged(value: String) {
        updateState { copy(properties = properties.copy(layoutWidth = value)) }
    }

    override fun onLayoutHeightChanged(value: String) {
        updateState { copy(properties = properties.copy(layoutHeight = value)) }
    }
}
