package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHistoryRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File

data class GitBlameUiState(
    val path: String = "",
    val lines: List<GitBlameLine> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class GitBlameViewModel(
    private val projectId: String,
    requestedPath: String,
    private val projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitHistoryRepository,
) : BaseViewModel<GitBlameUiState, Nothing>(GitBlameUiState(path = requestedPath)) {
    private var repoDir: File? = null

    init {
        tryToExecute(
            block = { projectPathResolver(projectId) },
            onSuccess = { repoDir = it; load() },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    private fun load() {
        val root = repoDir ?: return
        val path = state.value.path
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = { gitRepository.blame(root, path) },
            onSuccess = { updateState { copy(lines = it, loading = false) } },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }
}
