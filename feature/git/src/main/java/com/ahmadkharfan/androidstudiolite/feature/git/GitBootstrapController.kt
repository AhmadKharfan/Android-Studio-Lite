package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.domain.repository.GitLifecycleRepository
import java.io.File

internal class GitBootstrapController(
    private val context: GitPanelControllerContext,
    private val repository: GitLifecycleRepository,
    private val onRepositoryBootstrapped: (File) -> Unit,
) {
    fun onOpenBootstrap() = context.updateState { copy(bootstrapVisible = true) }

    fun onBootstrapInitialCommitChanged(enabled: Boolean) = context.updateState {
        copy(bootstrapInitialCommit = enabled)
    }

    fun onBootstrapMessageChanged(message: String) = context.updateState {
        copy(bootstrapMessage = message)
    }

    fun onConfirmBootstrap() {
        val repoDir = context.repoDir() ?: return
        val message = if (context.state().bootstrapInitialCommit) {
            context.state().bootstrapMessage.trim().ifBlank { "Initial commit" }
        } else {
            null
        }
        context.updateState { copy(bootstrapVisible = false) }
        context.execute(
            block = { repository.bootstrapRepository(repoDir, message) },
            onSuccess = {
                onRepositoryBootstrapped(repoDir)
                context.updateState { copy(statusMessage = "Version control enabled") }
            },
        )
    }

    fun onDismissBootstrap() = context.updateState { copy(bootstrapVisible = false) }
}
