package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitIntegrationRepository

internal class GitOperationsController(
    private val context: GitPanelControllerContext,
    private val repository: GitIntegrationRepository,
) {
    fun onContinueOperation() {
        val root = context.repoDir() ?: return
        if (context.state().repositoryState != GitRepositoryState.REBASING) return
        context.execute(block = { repository.rebaseContinue(root) })
    }

    fun onRequestAbortOperation() = context.updateState { copy(abortConfirmVisible = true) }

    fun onConfirmAbortOperation() {
        val root = context.repoDir() ?: return
        val repositoryState = context.state().repositoryState
        context.updateState { copy(abortConfirmVisible = false) }
        context.execute(
            block = {
                when (repositoryState) {
                    GitRepositoryState.MERGING -> repository.mergeAbort(root)
                    GitRepositoryState.REBASING -> repository.rebaseAbort(root)
                    GitRepositoryState.CHERRY_PICKING -> repository.cherryPickAbort(root)
                    GitRepositoryState.REVERTING -> repository.revertAbort(root)
                    GitRepositoryState.SAFE, GitRepositoryState.BISECTING -> Unit
                }
            },
        )
    }

    fun onDismissAbortOperation() = context.updateState { copy(abortConfirmVisible = false) }

    fun onRequestRestore(path: String) = context.updateState { copy(pendingRestorePaths = listOf(path)) }

    fun onConfirmRestore() {
        val root = context.repoDir() ?: return
        val paths = context.state().pendingRestorePaths
        context.updateState { copy(pendingRestorePaths = emptyList()) }
        context.execute(block = { repository.restoreFiles(root, paths) })
    }

    fun onDismissRestore() = context.updateState { copy(pendingRestorePaths = emptyList()) }

    fun onPreviewClean() {
        val root = context.repoDir() ?: return
        context.execute(
            block = { repository.clean(root, dryRun = true, includeIgnored = context.state().cleanIncludeIgnored) },
            onSuccess = { context.updateState { copy(cleanPreview = it) } },
        )
    }

    fun onCleanIncludeIgnoredChanged(include: Boolean) {
        context.updateState { copy(cleanIncludeIgnored = include, cleanPreview = null) }
        onPreviewClean()
    }

    fun onConfirmClean() {
        val root = context.repoDir() ?: return
        val include = context.state().cleanIncludeIgnored
        context.execute(
            block = { repository.clean(root, dryRun = false, includeIgnored = include) },
            onSuccess = { removed ->
                context.updateState { copy(cleanPreview = null, statusMessage = "Removed ${removed.size} path(s)") }
            },
        )
    }

    fun onDismissClean() = context.updateState { copy(cleanPreview = null) }
}
