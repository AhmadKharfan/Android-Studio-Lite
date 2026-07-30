package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCommitRepository

internal class GitCommitIdentityController(
    private val context: GitPanelControllerContext,
    private val repository: GitCommitRepository,
) {
    fun onOpenAuthorDialog() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.getAuthorConfig(repoDir) },
            onSuccess = { config ->
                val shown = config.local ?: config.effective
                context.updateState {
                    copy(authorDialogVisible = true, authorName = shown.name, authorEmail = shown.email)
                }
            },
        )
    }

    fun onAuthorNameChanged(name: String) = context.updateState { copy(authorName = name) }

    fun onAuthorEmailChanged(email: String) = context.updateState { copy(authorEmail = email) }

    fun onSaveLocalAuthor() {
        val repoDir = context.repoDir() ?: return
        val config = GitAuthorConfig(
            context.state().authorName.trim(),
            context.state().authorEmail.trim(),
        )
        if (config.name.isBlank() || config.email.isBlank()) return
        context.execute(
            block = { repository.setLocalAuthor(repoDir, config) },
            onSuccess = {
                context.updateState {
                    copy(authorDialogVisible = false, statusMessage = "Local Git author saved")
                }
            },
        )
    }

    fun onUseAppAuthor() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.setLocalAuthor(repoDir, null) },
            onSuccess = {
                context.updateState {
                    copy(authorDialogVisible = false, statusMessage = "Using app Git author")
                }
            },
        )
    }

    fun onDismissAuthorDialog() = context.updateState { copy(authorDialogVisible = false) }
}
