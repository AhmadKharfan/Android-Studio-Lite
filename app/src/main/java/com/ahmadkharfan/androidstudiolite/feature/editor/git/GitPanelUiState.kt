package com.ahmadkharfan.androidstudiolite.feature.editor.git
import androidx.compose.runtime.Immutable

import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemote
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmodule

@Immutable
data class GitChangeUiModel(
    val path: String,
    val displayPath: String = path,
    val status: GitFileStatus,
    val description: String? = null,
)

@Immutable
data class GitDiffLineUiModel(
    val kind: AslDiffKind,
    val text: String,
    val oldNo: Int?,
    val newNo: Int?,
)

@Immutable
data class GitPanelUiState(
    val branch: String = "—",
    val isRepository: Boolean = true,
    val loading: Boolean = true,
    val conflicts: List<GitChangeUiModel> = emptyList(),
    val stagedChanges: List<GitChangeUiModel> = emptyList(),
    val unstagedChanges: List<GitChangeUiModel> = emptyList(),
    val untrackedChanges: List<GitChangeUiModel> = emptyList(),
    val selectedPath: String? = null,
    val selectedDiffTarget: GitDiffTarget = GitDiffTarget.INDEX_TO_WORKTREE,
    val diffLines: List<GitDiffLineUiModel> = emptyList(),
    val commitMessage: String = "",
    val committing: Boolean = false,
    val authorDialogVisible: Boolean = false,
    val authorName: String = "",
    val authorEmail: String = "",
    val ahead: Int? = null,
    val behind: Int? = null,
    val syncing: Boolean = false,
    val busy: Boolean = false,
    val operationLabel: String? = null,
    val operationProgress: Float? = null,
    val operationCancellable: Boolean = false,
    val pullMode: PullMode = PullMode.MERGE,
    val forcePushConfirmVisible: Boolean = false,
    val remotesVisible: Boolean = false,
    val remotesLoading: Boolean = false,
    val remotes: List<GitRemote> = emptyList(),
    val remoteEditorVisible: Boolean = false,
    val editingRemoteName: String? = null,
    val remoteName: String = "",
    val remoteUrl: String = "",
    val remoteNameError: String? = null,
    val remoteUrlError: String? = null,
    /** Shown before/after a remote op needs auth: collect a GitHub sign-in or token, then retry. */
    val authPrompt: GitAuthPromptState = GitAuthPromptState(),
    val pendingRemoteRemoval: String? = null,
    val statusMessage: String? = null,
    val repositoryState: GitRepositoryState = GitRepositoryState.SAFE,
    val abortConfirmVisible: Boolean = false,
    val pendingRestorePaths: List<String> = emptyList(),
    val cleanPreview: List<String>? = null,
    val cleanIncludeIgnored: Boolean = false,
    val submodulesVisible: Boolean = false,
    val submodulesLoading: Boolean = false,
    val submodules: List<GitSubmodule> = emptyList(),
    val bootstrapVisible: Boolean = false,
    val bootstrapInitialCommit: Boolean = true,
    val bootstrapMessage: String = "Initial commit",
    /** Paths the user has ticked for a bulk stage/unstage/revert. Pruned to the live change set. */
    val selectedPaths: Set<String> = emptySet(),
) {
    val hasChanges: Boolean get() =
        conflicts.isNotEmpty() || stagedChanges.isNotEmpty() ||
            unstagedChanges.isNotEmpty() || untrackedChanges.isNotEmpty()
    /**
     * Commit is allowed when there's a message, no conflicts and something to commit. "Something" is
     * any staged change, or — for convenience — any unstaged/untracked change (the panel auto-stages
     * the selected files, or all changes when none are ticked, right before committing).
     */
    val canCommit: Boolean get() = commitMessage.isNotBlank() && conflicts.isEmpty() &&
        (stagedChanges.isNotEmpty() || unstagedChanges.isNotEmpty() || untrackedChanges.isNotEmpty()) &&
        !committing && !busy

    /** Distinct paths across every section (a path may be both staged and modified). */
    val allChangePaths: Set<String> get() =
        buildSet {
            stagedChanges.forEach { add(it.path) }
            unstagedChanges.forEach { add(it.path) }
            untrackedChanges.forEach { add(it.path) }
        }
    private val stagedPaths: Set<String> get() = stagedChanges.mapTo(mutableSetOf()) { it.path }
    private val unstagedOrUntrackedPaths: Set<String> get() =
        buildSet { unstagedChanges.forEach { add(it.path) }; untrackedChanges.forEach { add(it.path) } }
    private val revertablePaths: Set<String> get() = unstagedChanges.mapTo(mutableSetOf()) { it.path }

    val selectionCount: Int get() = selectedPaths.size
    val hasSelection: Boolean get() = selectedPaths.isNotEmpty()
    val allSelected: Boolean get() = allChangePaths.isNotEmpty() && selectedPaths.containsAll(allChangePaths)
    /** Selection actions are only offered when they can act on at least one selected file. */
    val canStageSelection: Boolean get() = !busy && selectedPaths.any { it in unstagedOrUntrackedPaths }
    val canUnstageSelection: Boolean get() = !busy && selectedPaths.any { it in stagedPaths }
    val canRevertSelection: Boolean get() = !busy && selectedPaths.any { it in revertablePaths }
    fun revertableSelection(): List<String> = selectedPaths.filter { it in revertablePaths }
}

internal data class GitPanelSections(
    val conflicts: List<GitChangeUiModel>,
    val staged: List<GitChangeUiModel>,
    val unstaged: List<GitChangeUiModel>,
    val untracked: List<GitChangeUiModel>,
)

internal fun GitState.toPanelSections(): GitPanelSections {
    val conflicts = mutableListOf<GitChangeUiModel>()
    val staged = mutableListOf<GitChangeUiModel>()
    val unstaged = mutableListOf<GitChangeUiModel>()
    val untracked = mutableListOf<GitChangeUiModel>()
    files.forEach { file ->
        val conflict = file.conflictStage
        if (conflict != null) {
            conflicts += file.toUiModel(
                status = GitFileStatus.CONFLICTED,
                description = conflict.description,
            )
            return@forEach
        }
        file.indexStatus.toFileStatus()?.let { staged += file.toUiModel(it) }
        file.worktreeStatus.toTrackedFileStatus()?.let { unstaged += file.toUiModel(it) }
        if (file.worktreeStatus == GitWorktreeStatus.UNTRACKED) {
            untracked += file.toUiModel(GitFileStatus.UNTRACKED)
        }
    }
    return GitPanelSections(conflicts, staged, unstaged, untracked)
}

private fun GitFileState.toUiModel(status: GitFileStatus, description: String? = null) = GitChangeUiModel(
    path = path,
    displayPath = oldPath?.let { "$it → $path" } ?: path,
    status = status,
    description = description,
)

private fun GitIndexStatus.toFileStatus(): GitFileStatus? = when (this) {
    GitIndexStatus.ADDED -> GitFileStatus.ADDED
    GitIndexStatus.MODIFIED, GitIndexStatus.RENAMED -> GitFileStatus.MODIFIED
    GitIndexStatus.DELETED -> GitFileStatus.DELETED
    GitIndexStatus.UNCHANGED -> null
}

private fun GitWorktreeStatus.toTrackedFileStatus(): GitFileStatus? = when (this) {
    GitWorktreeStatus.MODIFIED -> GitFileStatus.MODIFIED
    GitWorktreeStatus.DELETED -> GitFileStatus.DELETED
    GitWorktreeStatus.UNTRACKED, GitWorktreeStatus.IGNORED, GitWorktreeStatus.UNCHANGED -> null
}
