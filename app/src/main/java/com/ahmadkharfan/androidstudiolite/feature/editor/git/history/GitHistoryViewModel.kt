package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitDetails
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.git.gitErrorMessage
import java.io.File
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode

data class GitHistoryUiState(
    val path: String? = null,
    val commits: List<GitCommitSummary> = emptyList(),
    val nextCursor: String? = null,
    val selected: GitCommitDetails? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val shallow: Boolean = false,
    val error: String? = null,
    val graphRows: Map<String, GitGraphRow> = emptyMap(),
    val graphEnabled: Boolean = true,
)

class GitHistoryViewModel(
    private val projectId: String,
    requestedPath: String?,
    private val projectPathResolver: ProjectPathResolver,
    private val gitRepository: GitRepository,
) : BaseViewModel<GitHistoryUiState, Nothing>(GitHistoryUiState()) {
    private var repoDir: File? = null
    private var path: String? = null
    private val graphComputer = GitGraphLaneComputer()
    private var graphCursor = GitGraphCursor()
    private var graphExplicitlySelected = false

    init {
        tryToExecute(
            block = {
                projectPathResolver(projectId).also { root -> path = requestedPath?.toRepoPath(root) }
            },
            onSuccess = { root ->
                repoDir = root
                updateState { copy(path = path, graphEnabled = path == null) }
                loadFirst()
            },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun loadNext() {
        val cursor = state.value.nextCursor ?: return
        if (state.value.loading || state.value.loadingMore) return
        load(cursor)
    }

    fun select(commitId: String) {
        val root = repoDir ?: return
        tryToExecute(
            block = { gitRepository.commitDetails(root, commitId) },
            onSuccess = { details -> updateState { copy(selected = details, error = null) } },
            onError = { updateState { copy(error = gitErrorMessage(it)) } },
        )
    }

    fun clearSelection() = updateState { copy(selected = null) }

    fun deepen() {
        val root = repoDir ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = { gitRepository.deepen(root) },
            onSuccess = { loadFirst() },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun reset(commitId: String, mode: GitResetMode) {
        val root = repoDir ?: return
        updateState { copy(loading = true, error = null) }
        tryToExecute(
            block = { gitRepository.reset(root, commitId, mode) },
            onSuccess = { loadFirst() },
            onError = { updateState { copy(loading = false, error = gitErrorMessage(it)) } },
        )
    }

    fun toggleGraph() {
        graphExplicitlySelected = true
        updateState { copy(graphEnabled = !graphEnabled) }
    }

    private fun loadFirst() {
        graphCursor = GitGraphCursor()
        updateState { copy(commits = emptyList(), graphRows = emptyMap(), nextCursor = null, selected = null, loading = true, error = null) }
        load(cursor = null)
    }

    private fun load(cursor: String?) {
        val root = repoDir ?: return
        updateState {
            if (cursor == null) copy(loading = true) else copy(loadingMore = true)
        }
        tryToExecute(
            block = {
                val page = path?.let { gitRepository.fileHistory(root, it, cursor, PAGE_SIZE) }
                    ?: gitRepository.log(root, cursor, PAGE_SIZE)
                page to gitRepository.isShallow(root)
            },
            onSuccess = { (page, shallow) ->
                val graphPage = if (path == null) {
                    graphComputer.layout(page.commits, graphCursor)
                } else {
                    GitGraphPage(emptyList(), graphCursor)
                }
                graphCursor = graphPage.nextCursor
                updateState {
                    val updatedCommits = if (cursor == null) page.commits else commits + page.commits
                    copy(
                        commits = updatedCommits,
                        graphRows = if (cursor == null) graphPage.rows.associateBy { it.commitId }
                            else graphRows + graphPage.rows.associateBy { it.commitId },
                        graphEnabled = if (!graphExplicitlySelected && updatedCommits.size >= GRAPH_AUTO_LIMIT) false else graphEnabled,
                        nextCursor = page.nextCursor,
                        shallow = shallow,
                        loading = false,
                        loadingMore = false,
                    )
                }
            },
            onError = {
                updateState { copy(loading = false, loadingMore = false, error = gitErrorMessage(it)) }
            },
        )
    }

    private fun String.toRepoPath(root: File): String {
        val candidate = File(this)
        return if (candidate.isAbsolute) {
            runCatching { candidate.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath }
                .getOrDefault(this)
        } else {
            replace('\\', '/')
        }
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val GRAPH_AUTO_LIMIT = 5_000
    }
}
