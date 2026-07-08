package com.example.androidstudiolite.feature.openproject.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.util.formatRelativeTime
import com.example.androidstudiolite.di.AppContainer
import com.example.androidstudiolite.domain.model.Project
import com.example.androidstudiolite.domain.usecase.GetRecentProjectsUseCase
import com.example.androidstudiolite.feature.openproject.interaction.OpenProjectInteraction
import com.example.androidstudiolite.feature.openproject.uiState.OpenProjectItemUiModel
import com.example.androidstudiolite.feature.openproject.uiState.OpenProjectUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OpenProjectViewModel(
    private val getRecentProjects: GetRecentProjectsUseCase = GetRecentProjectsUseCase(AppContainer.projectRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpenProjectUiState())
    val uiState: StateFlow<OpenProjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getRecentProjects().collect { projects ->
                val models = projects.map { it.toUiModel() }
                _uiState.value = _uiState.value.copy(allProjects = models, filteredProjects = filter(models, _uiState.value.query))
            }
        }
    }

    fun onInteraction(interaction: OpenProjectInteraction) {
        when (interaction) {
            is OpenProjectInteraction.QueryChanged -> {
                _uiState.value = _uiState.value.copy(
                    query = interaction.query,
                    filteredProjects = filter(_uiState.value.allProjects, interaction.query),
                )
            }
            is OpenProjectInteraction.SelectProject -> Unit
        }
    }

    private fun filter(models: List<OpenProjectItemUiModel>, query: String): List<OpenProjectItemUiModel> =
        if (query.isBlank()) models else models.filter { it.name.contains(query, ignoreCase = true) }

    private fun Project.toUiModel() = OpenProjectItemUiModel(
        id = id,
        name = name,
        subtitle = path + (lastOpenedMillis?.let { " · ${formatRelativeTime(it)}" } ?: ""),
    )
}
