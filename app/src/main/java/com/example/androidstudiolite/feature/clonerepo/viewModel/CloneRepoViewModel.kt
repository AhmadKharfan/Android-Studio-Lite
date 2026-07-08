package com.example.androidstudiolite.feature.clonerepo.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.usecase.CloneRepositoryUseCase
import com.example.androidstudiolite.feature.clonerepo.interaction.CloneRepoInteraction
import com.example.androidstudiolite.feature.clonerepo.uiState.CloneRepoUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CloneRepoViewModel(
    private val cloneRepository: CloneRepositoryUseCase = CloneRepositoryUseCase(AppContainer.projectRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneRepoUiState())
    val uiState: StateFlow<CloneRepoUiState> = _uiState.asStateFlow()

    fun onInteraction(interaction: CloneRepoInteraction) {
        when (interaction) {
            is CloneRepoInteraction.UrlChanged -> _uiState.value = _uiState.value.copy(url = interaction.url)
            is CloneRepoInteraction.BranchChanged -> _uiState.value = _uiState.value.copy(branch = interaction.branch)
            is CloneRepoInteraction.ToggleOption -> _uiState.value = _uiState.value.copy(
                options = _uiState.value.options.map { if (it.id == interaction.id) it.copy(selected = !it.selected) else it },
            )
            CloneRepoInteraction.StartClone -> startClone()
        }
    }

    private fun startClone() {
        val state = _uiState.value
        if (state.cloning || state.url.isBlank()) return
        _uiState.value = state.copy(cloning = true)
        viewModelScope.launch {
            cloneRepository(state.url, state.branch.ifBlank { null }).collect { progress ->
                _uiState.value = _uiState.value.copy(
                    progressPercent = ((progress.fraction ?: 0f) * 100).toInt(),
                    progressMessage = progress.message,
                    clonedProjectId = progress.clonedProjectId,
                )
            }
        }
    }
}
