package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.domain.repository.GitSubmoduleRepository

internal class GitSubmodulesController(
    private val context: GitPanelControllerContext,
    private val repository: GitSubmoduleRepository,
) {
    fun onOpenSubmodules() {
        context.updateState { copy(submodulesVisible = true, submodulesLoading = true) }
        reloadSubmodules()
    }

    fun onCloseSubmodules() = context.updateState { copy(submodulesVisible = false) }

    fun onInitSubmodules() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.submoduleInit(repoDir) },
            onSuccess = {
                context.updateState { copy(statusMessage = "Submodules initialised") }
                reloadSubmodules()
            },
        )
    }

    fun onUpdateSubmodules() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.submoduleUpdate(repoDir) },
            onSuccess = {
                context.updateState { copy(statusMessage = "Submodules updated") }
                reloadSubmodules()
            },
        )
    }

    private fun reloadSubmodules() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.submodules(repoDir) },
            onSuccess = {
                context.updateState { copy(submodules = it, submodulesLoading = false) }
            },
            onError = {
                context.updateState { copy(submodulesLoading = false) }
                context.showError(it)
            },
        )
    }
}
