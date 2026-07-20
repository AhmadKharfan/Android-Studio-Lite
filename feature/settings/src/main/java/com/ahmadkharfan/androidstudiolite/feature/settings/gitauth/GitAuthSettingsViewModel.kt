package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthController
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitAuthMode
import kotlinx.coroutines.launch

/**
 * Manages global GitHub authentication (device-flow sign-in or a personal access token, stored per
 * host for `github.com`) and the app-wide Git commit author identity. Credentials are shared across
 * every project, so signing in here unlocks push/pull everywhere.
 */
class GitAuthSettingsViewModel(
    private val credentialStore: GitCredentialStore,
    private val authenticator: GitHubDeviceAuthenticator,
    private val gitAuthorStore: GitAuthorStore,
) : BaseViewModel<GitAuthSettingsUiState, Nothing>(
    initialState = GitAuthSettingsUiState(gitHubAvailable = authenticator.isConfigured),
), GitAuthSettingsInteractionListener {

    private var authorTouched = false

    private val authController = GitAuthController(
        scope = viewModelScope,
        credentialStore = credentialStore,
        authenticator = authenticator,
        emit = { prompt -> updateState { copy(authPrompt = prompt) } },
    )

    init {
        refreshConnection()
        tryToCollect(block = { credentialStore.changes }, onCollect = { refreshConnection() })
        tryToCollect(
            block = { gitAuthorStore.observe() },
            onCollect = { author ->
                if (!authorTouched) {
                    updateState {
                        copy(
                            gitAuthorName = author?.name.orEmpty(),
                            gitAuthorEmail = author?.email.orEmpty(),
                            gitAuthorDirty = false,
                        )
                    }
                }
            },
        )
    }

    private fun refreshConnection() =
        updateState { copy(gitHubConnected = credentialStore.hasCredentials(GITHUB_HOST)) }

    override fun onConnectGitHub() = authController.open(GITHUB_HOST) {
        updateState { copy(statusMessage = "GitHub connected", isError = false) }
        refreshConnection()
    }

    override fun onDisconnectGitHub() {
        credentialStore.clear(GITHUB_HOST)
        updateState { copy(statusMessage = "Signed out of GitHub", isError = false) }
        refreshConnection()
    }

    override fun onGitAuthorNameChanged(name: String) {
        authorTouched = true
        updateState { copy(gitAuthorName = name, gitAuthorDirty = true) }
    }

    override fun onGitAuthorEmailChanged(email: String) {
        authorTouched = true
        updateState { copy(gitAuthorEmail = email, gitAuthorDirty = true) }
    }

    override fun onSaveGitAuthor() {
        val config = GitAuthorConfig(state.value.gitAuthorName.trim(), state.value.gitAuthorEmail.trim())
        if (config.name.isBlank() || config.email.isBlank()) {
            updateState { copy(statusMessage = "Enter a Git author name and email", isError = true) }
            return
        }
        viewModelScope.launch {
            gitAuthorStore.set(config)
            authorTouched = false
            updateState { copy(gitAuthorDirty = false, statusMessage = "Git author saved", isError = false) }
        }
    }

    override fun onStatusMessageShown() = updateState { copy(statusMessage = null) }

    // Shared auth dialog callbacks.
    override fun onAuthModeChanged(mode: GitAuthMode) = authController.onAuthModeChanged(mode)
    override fun onAuthTokenChanged(token: String) = authController.onAuthTokenChanged(token)
    override fun onSubmitAuthToken() = authController.onSubmitAuthToken()
    override fun onStartGitHubSignIn() = authController.onStartGitHubSignIn()
    override fun onDismissAuthPrompt() = authController.onDismissAuthPrompt()

    private companion object {
        const val GITHUB_HOST = "github.com"
    }
}
