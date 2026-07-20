package com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictEntry
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File

data class GitConflictUiState(
    val rootPath: String = "",
    val entries: List<GitConflictEntry> = emptyList(),
    val loading: Boolean = true,
    val markerOverridePath: String? = null,
    val error: String? = null,
)

class GitConflictViewModel(
    projectId: String,
    projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
) : BaseViewModel<GitConflictUiState, Nothing>(GitConflictUiState()), GitConflictInteractionListener {
    private var root: File? = null

    init {
        tryToExecute(
            block = { projectPathResolver(projectId) },
            onSuccess = { root = it; updateState { copy(rootPath = it.absolutePath) }; refresh() },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun refresh() {
        val repo = root ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = { gitRepository.conflictEntries(repo) },
            onSuccess = { updateState { copy(entries = it, loading = false) } },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    override fun acceptOurs(path: String) = mutate { gitRepository.resolveAcceptOurs(requireRoot(), path) }
    override fun acceptTheirs(path: String) = mutate { gitRepository.resolveAcceptTheirs(requireRoot(), path) }

    override fun markResolved(path: String, allowMarkers: Boolean) {
        val entry = state.value.entries.firstOrNull { it.path == path } ?: return
        if (!allowMarkers && entry.worktree.hasConflictMarkers()) {
            updateState { copy(markerOverridePath = path) }
            return
        }
        updateState { copy(markerOverridePath = null) }
        mutate { gitRepository.markResolved(requireRoot(), path) }
    }

    override fun dismissMarkerWarning() = updateState { copy(markerOverridePath = null) }

    private fun mutate(block: suspend () -> Unit) {
        updateState { copy(loading = true, error = null) }
        tryToExecute(block = block, onSuccess = { refresh() }, onError = {
            updateState { copy(loading = false, error = gitErrorMessage(it)) }
        })
    }

    private fun requireRoot() = checkNotNull(root)

    private fun String?.hasConflictMarkers(): Boolean = this != null &&
        lineSequence().any { it.startsWith("<<<<<<<") || it.startsWith("=======") || it.startsWith(">>>>>>>") }
}
