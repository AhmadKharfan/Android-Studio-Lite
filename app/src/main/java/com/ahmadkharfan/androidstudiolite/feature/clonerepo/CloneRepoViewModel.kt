package com.ahmadkharfan.androidstudiolite.feature.clonerepo

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.usecase.CloneProjectUseCase
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import kotlinx.coroutines.Job

class CloneRepoViewModel(
    private val cloneProject: CloneProjectUseCase,
) : BaseViewModel<CloneRepoUiState, Nothing>(
    initialState = CloneRepoUiState(),
), CloneRepoInteractionListener {

    private var cloneJob: Job? = null

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
        val selectedOptions = current.options.filter { it.selected }.mapTo(mutableSetOf()) { it.id }
        val options = CloneOptions(
            branch = current.branch.ifBlank { null },
            depth = if ("depth1" in selectedOptions) 1 else null,
            singleBranch = "singleBranch" in selectedOptions,
            recursiveSubmodules = "recursive" in selectedOptions,
        )
        updateState { copy(cloning = true, error = null, progressPercent = 0, progressMessage = "Starting…") }
        cloneJob = tryToCollect(
            block = { cloneProject.clone(current.url.trim(), options, credentials) },
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
                    copy(cloning = false, error = gitErrorMessage(throwable))
                }
            },
        )
    }

    override fun onCancelClone() {
        cloneJob?.cancel()
        cloneJob = null
        updateState {
            copy(
                cloning = false,
                progressPercent = 0,
                progressMessage = "",
                clonedProjectId = null,
                error = null,
            )
        }
    }
}
