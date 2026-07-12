package com.ahmadkharfan.androidstudiolite.feature.openproject

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.formatRelativeTime

class OpenProjectViewModel(
    private val projectRepository: ProjectRepository,
) : BaseViewModel<OpenProjectUiState, OpenProjectEffect>(
    initialState = OpenProjectUiState(),
), OpenProjectInteractionListener {

    init {
        tryToCollect(
            block = { projectRepository.observeRecentProjects() },
            onCollect = { projects ->
                val models = projects.map { it.toUiModel() }
                updateState { copy(allProjects = models, filteredProjects = filter(models, query)) }
            },
        )
    }

    override fun onQueryChanged(query: String) {
        updateState { copy(query = query, filteredProjects = filter(allProjects, query)) }
    }

    override fun onSelectProject(id: String) {
        emitEffect(OpenProjectEffect.NavigateToProject(id))
    }

    private fun filter(models: List<OpenProjectItemUiModel>, query: String): List<OpenProjectItemUiModel> =
        if (query.isBlank()) models else models.filter { it.name.contains(query, ignoreCase = true) }

    private fun Project.toUiModel() = OpenProjectItemUiModel(
        id = id,
        name = name,
        subtitle = path + (lastOpenedMillis?.let { " · ${formatRelativeTime(it)}" } ?: ""),
    )
}
