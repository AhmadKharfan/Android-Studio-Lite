package com.example.androidstudiolite.feature.settings.ideconfig

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.model.IdeComponent
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import kotlinx.coroutines.delay

class IdeConfigViewModel(
    private val ideConfigRepository: IdeConfigRepository,
) : BaseViewModel<IdeConfigUiState, Nothing>(
    initialState = IdeConfigUiState(),
), IdeConfigInteractionListener {

    init {
        tryToCollect(
            block = { ideConfigRepository.observeState() },
            onCollect = { ideState ->
                updateState {
                    copy(
                        components = ideState.components.map { it.toUiModel() },
                        offlineMode = ideState.offlineMode,
                        networkAvailable = ideState.networkAvailable,
                    )
                }
            },
        )
    }

    override fun onInstallComponent(id: String) {
        tryToExecute(block = { ideConfigRepository.installComponent(id) })
    }

    override fun onToggleOfflineMode(enabled: Boolean) {
        tryToExecute(block = { ideConfigRepository.setOfflineMode(enabled) })
    }

    override fun onRetryConnection() {
        tryToExecute(
            block = {
                delay(700)
                ideConfigRepository.setNetworkAvailable(true)
            },
        )
    }

    private fun IdeComponent.toUiModel() = IdeComponentUiModel(
        id = id,
        icon = icon,
        title = title,
        subtitle = subtitle,
        status = status,
    )
}
