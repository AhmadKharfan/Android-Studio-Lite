package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.domain.model.GitBlameLine
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHistoryRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitViewModel
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
) : GitViewModel<GitBlameUiState, Nothing>(GitBlameUiState(path = requestedPath)) {
    private var repoDir: File? = null

    override fun GitBlameUiState.withGitError(message: String) = copy(loading = false, error = message)

    init {
        tryToExecute(
            block = { projectPathResolver(projectId) },
            onSuccess = { repoDir = it; load() },
            onError = gitErrorHandler(),
        )
    }

    private fun load() {
        val root = repoDir ?: return
        val path = state.value.path
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = { gitRepository.blame(root, path) },
            onSuccess = { updateState { copy(lines = it, loading = false) } },
            onError = gitErrorHandler(),
        )
    }
}
