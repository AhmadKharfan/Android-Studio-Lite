package com.ahmadkharfan.androidstudiolite.feature.editor.git.diff

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File

data class GitDiffUiState(
    val path: String,
    val target: GitDiffTarget,
    val diff: GitFileDiff? = null,
    val loading: Boolean = true,
    val sideBySide: Boolean = false,
    val error: String? = null,
)

class GitDiffViewModel(
    private val projectId: String,
    path: String,
    private val target: GitDiffTarget,
    private val commitId: String?,
    private val projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
) : BaseViewModel<GitDiffUiState, Nothing>(GitDiffUiState(path, target)) {

    private var repoDir: File? = null

    init {
        tryToExecute(
            block = { projectPathResolver(projectId) },
            onSuccess = {
                repoDir = it
                load(force = false)
            },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun setSideBySide(enabled: Boolean) = updateState { copy(sideBySide = enabled) }

    fun showAnyway() = load(force = true)

    fun stage(hunk: GitDiffHunk) = updateHunk(hunk, unstage = false)

    fun unstage(hunk: GitDiffHunk) = updateHunk(hunk, unstage = true)

    private fun updateHunk(hunk: GitDiffHunk, unstage: Boolean) {
        val root = repoDir ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = {
                if (unstage) gitRepository.unstageHunk(root, state.value.path, hunk)
                else gitRepository.stageHunk(root, state.value.path, hunk)
            },
            onSuccess = { load(force = false) },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    private fun load(force: Boolean) {
        val root = repoDir ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = {
                when (target) {
                    GitDiffTarget.INDEX_TO_WORKTREE -> gitRepository.diffIndexToWorktree(root, state.value.path, force)
                    GitDiffTarget.HEAD_TO_INDEX -> gitRepository.diffHeadToIndex(root, state.value.path, force)
                    GitDiffTarget.COMMIT_TO_PARENT -> gitRepository.diffCommitToParent(
                        root,
                        checkNotNull(commitId) { "Commit id is required for a historical diff" },
                        state.value.path,
                        force,
                    )
                }
            },
            onSuccess = { updateState { copy(diff = it, loading = false) } },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }
}
