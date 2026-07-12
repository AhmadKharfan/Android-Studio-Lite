package com.ahmadkharfan.androidstudiolite.feature.clonerepo

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository

class CloneRepoViewModel(
    private val gitRepository: GitRepository,
) : BaseViewModel<CloneRepoUiState, Nothing>(
    initialState = CloneRepoUiState(),
), CloneRepoInteractionListener {

    override fun onUrlChanged(url: String) {
        updateState { copy(url = url, error = null) }
    }

    override fun onBranchChanged(branch: String) {
        updateState { copy(branch = branch) }
    }

    override fun onTokenChanged(token: String) {
        updateState { copy(token = token) }
    }

    override fun onToggleOption(id: String) {
        updateState {
            copy(options = options.map { if (it.id == id) it.copy(selected = !it.selected) else it })
        }
    }

    override fun onStartClone() {
        val current = state.value
        if (current.cloning || current.url.isBlank()) return
        val credentials = current.token.takeIf { it.isNotBlank() }
            ?.let { GitCredentials(username = "", token = it) }
        updateState { copy(cloning = true, error = null, progressPercent = 0, progressMessage = "Starting…") }
        tryToCollect(
            block = { gitRepository.clone(current.url.trim(), current.branch.ifBlank { null }, credentials) },
            onCollect = { progress ->
                updateState {
                    copy(
                        progressPercent = ((progress.fraction ?: 0f) * 100).toInt(),
                        progressMessage = progress.message,
                        clonedProjectId = progress.clonedProjectId,
                    )
                }
            },
            onError = { throwable ->
                updateState {
                    copy(cloning = false, error = throwable.message ?: "Clone failed")
                }
            },
        )
    }
}
