package com.ahmadkharfan.androidstudiolite.feature.git

import com.ahmadkharfan.androidstudiolite.domain.repository.GitRemoteRepository
import java.io.File
import java.net.URI

internal class GitRemotesController(
    private val context: GitPanelControllerContext,
    private val repository: GitRemoteRepository,
) {
    fun onOpenRemotes() {
        context.updateState { copy(remotesVisible = true, remotesLoading = true) }
        reloadRemotes()
    }

    fun onCloseRemotes() = context.updateState { copy(remotesVisible = false) }

    fun onAddRemote() = context.updateState {
        copy(
            remoteEditorVisible = true,
            editingRemoteName = null,
            remoteName = "",
            remoteUrl = "",
            remoteNameError = null,
            remoteUrlError = null,
        )
    }

    fun onEditRemote(name: String) {
        val remote = context.state().remotes.firstOrNull { it.name == name } ?: return
        context.updateState {
            copy(
                remoteEditorVisible = true,
                editingRemoteName = name,
                remoteName = name,
                remoteUrl = remote.url,
                remoteNameError = null,
                remoteUrlError = null,
            )
        }
    }

    fun onRemoteNameChanged(name: String) = context.updateState {
        copy(remoteName = name, remoteNameError = null)
    }

    fun onRemoteUrlChanged(url: String) = context.updateState {
        copy(remoteUrl = url, remoteUrlError = null)
    }

    fun onSaveRemote() {
        val repoDir = context.repoDir() ?: return
        val editing = context.state().editingRemoteName
        val url = context.state().remoteUrl.trim()

        val urlError = if (isSupportedRemoteUrl(url)) null else "Use an http(s):// or file:// URL"
        if (urlError != null) {
            context.updateState { copy(remoteUrlError = urlError) }
            return
        }
        val targetName = editing ?: "origin".takeIf {
            context.state().remotes.any { remote -> remote.name == "origin" }
        }
        context.execute(
            block = { saveRemote(repoDir, targetName, url) },
            onSuccess = {
                context.updateState { copy(remoteEditorVisible = false, statusMessage = "Remote saved") }
                reloadRemotes()
            },
        )
    }

    fun onDismissRemoteEditor() = context.updateState { copy(remoteEditorVisible = false) }

    fun onRequestRemoveRemote(name: String) = context.updateState { copy(pendingRemoteRemoval = name) }

    fun onConfirmRemoveRemote() {
        val repoDir = context.repoDir() ?: return
        val name = context.state().pendingRemoteRemoval ?: return
        context.execute(
            block = { repository.removeRemote(repoDir, name) },
            onSuccess = {
                context.updateState { copy(pendingRemoteRemoval = null, statusMessage = "Removed $name") }
                reloadRemotes()
            },
        )
    }

    fun onDismissRemoveRemote() = context.updateState { copy(pendingRemoteRemoval = null) }

    private suspend fun saveRemote(
        repoDir: File,
        targetName: String?,
        url: String,
    ) {
        if (targetName == null) repository.addRemote(repoDir, "origin", url)
        else repository.setRemoteUrl(repoDir, targetName, url)
    }

    private fun reloadRemotes() {
        val repoDir = context.repoDir() ?: return
        context.execute(
            block = { repository.listRemotes(repoDir) },
            onSuccess = { remotes ->
                context.updateState { copy(remotes = remotes, remotesLoading = false) }
            },
            onError = {
                context.updateState { copy(remotesLoading = false) }
                context.showError(it)
            },
        )
    }

    private fun isSupportedRemoteUrl(value: String): Boolean {
        val normalized = value.lowercase()
        if (SUPPORTED_REMOTE_PREFIXES.none(normalized::startsWith)) return false
        return runCatching { URI(value).scheme?.lowercase() in SUPPORTED_REMOTE_SCHEMES }.getOrDefault(false)
    }

    private companion object {
        val SUPPORTED_REMOTE_SCHEMES = setOf("http", "https", "file")
        val SUPPORTED_REMOTE_PREFIXES = listOf("http://", "https://", "file://")
    }
}
