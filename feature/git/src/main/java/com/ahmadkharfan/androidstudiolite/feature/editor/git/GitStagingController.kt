package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff
import com.ahmadkharfan.androidstudiolite.domain.repository.GitDiffRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.GitStagingRepository

internal class GitStagingController(
    private val context: GitPanelControllerContext,
    private val stagingRepository: GitStagingRepository,
    private val diffRepository: GitDiffRepository,
) {
    fun onSelectChange(path: String, target: GitDiffTarget) {
        val repoDir = context.repoDir() ?: return
        context.updateState { copy(selectedPath = path, selectedDiffTarget = target) }
        context.execute(
            block = {
                when (target) {
                    GitDiffTarget.INDEX_TO_WORKTREE -> diffRepository.diffIndexToWorktree(repoDir, path)
                    GitDiffTarget.HEAD_TO_INDEX -> diffRepository.diffHeadToIndex(repoDir, path)
                    GitDiffTarget.COMMIT_TO_PARENT -> error("Commit diffs are opened from history")
                }
            },
            onSuccess = { diff ->
                context.updateState { copy(diffLines = diff.toUiLines()) }
            },
        )
    }

    fun onCloseDiff() = context.updateState { copy(selectedPath = null, diffLines = emptyList()) }

    fun onStage(path: String) {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        context.execute(block = { stagingRepository.stage(repoDir, path) })
    }

    fun onUnstage(path: String) {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        context.execute(block = { stagingRepository.unstage(repoDir, path) })
    }

    fun onStageAll() {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        context.execute(block = { stagingRepository.stageAll(repoDir) })
    }

    fun onUnstageAll() {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        context.execute(block = { stagingRepository.unstageAll(repoDir) })
    }

    fun onSelectAllChanges() = context.updateState { copy(selectedPaths = allChangePaths) }

    fun onClearSelection() = context.updateState { copy(selectedPaths = emptySet()) }

    fun onToggleSelect(path: String) = context.updateState {
        copy(selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path)
    }

    fun onToggleSectionSelect(paths: List<String>, select: Boolean) = context.updateState {
        copy(selectedPaths = if (select) selectedPaths + paths else selectedPaths - paths.toSet())
    }

    fun onStageSelected() {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        val paths = context.state().selectedPaths.filter { path ->
            context.state().unstagedChanges.any { it.path == path } ||
                context.state().untrackedChanges.any { it.path == path }
        }
        if (paths.isEmpty()) return
        context.execute(
            block = { paths.forEach { stagingRepository.stage(repoDir, it) } },
            onSuccess = { context.updateState { copy(selectedPaths = emptySet()) } },
        )
    }

    fun onUnstageSelected() {
        if (context.state().isBusy) return
        val repoDir = context.repoDir() ?: return
        val paths = context.state().selectedPaths.filter { path ->
            context.state().stagedChanges.any { it.path == path }
        }
        if (paths.isEmpty()) return
        context.execute(
            block = { paths.forEach { stagingRepository.unstage(repoDir, it) } },
            onSuccess = { context.updateState { copy(selectedPaths = emptySet()) } },
        )
    }

    fun onRevertSelected() {
        val paths = context.state().revertableSelection()
        if (paths.isEmpty()) return
        context.updateState { copy(pendingRestorePaths = paths) }
    }

    private fun GitFileDiff.toUiLines(): List<GitDiffLineUiModel> = hunks.flatMap { it.lines }.map {
        GitDiffLineUiModel(
            kind = it.kind.toAslDiffKind(),
            text = it.text,
            oldNo = it.oldNo,
            newNo = it.newNo,
        )
    }

    private fun GitDiffKind.toAslDiffKind(): AslDiffKind = when (this) {
        GitDiffKind.ADDED -> AslDiffKind.Added
        GitDiffKind.REMOVED -> AslDiffKind.Removed
        GitDiffKind.MODIFIED -> AslDiffKind.Modified
        GitDiffKind.CONTEXT -> AslDiffKind.Context
    }
}
