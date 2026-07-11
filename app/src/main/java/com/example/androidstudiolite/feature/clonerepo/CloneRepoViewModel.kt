package com.example.androidstudiolite.feature.clonerepo

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.ProjectRepository

class CloneRepoViewModel(
    private val projectRepository: ProjectRepository,
) : BaseViewModel<CloneRepoUiState, Nothing>(
    initialState = CloneRepoUiState(),
), CloneRepoInteractionListener {

    override fun onUrlChanged(url: String) {
        updateState { copy(url = url) }
    }

    override fun onBranchChanged(branch: String) {
        updateState { copy(branch = branch) }
    }

    override fun onToggleOption(id: String) {
        updateState {
            copy(options = options.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    override fun onStartClone() {
        val current = state.value
        if (current.cloning || current.url.isBlank()) return
        updateState { copy(cloning = true) }
        tryToCollect(
            block = { projectRepository.cloneRepository(current.url, current.branch.ifBlank { null }) },
            onCollect = { progress ->
                updateState {
                    copy(
                        progressPercent = ((progress.fraction ?: 0f) * 100).toInt(),
                        progressMessage = progress.message,
                        clonedProjectId = progress.clonedProjectId,
                    )
                }
            },
        )
    }
}
