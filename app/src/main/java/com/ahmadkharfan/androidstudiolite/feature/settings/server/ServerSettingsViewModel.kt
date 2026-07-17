package com.ahmadkharfan.androidstudiolite.feature.settings.server

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteClient
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteException
import com.ahmadkharfan.androidstudiolite.data.remote.ServerSettingsRepository
import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import kotlinx.coroutines.launch

/**
 * Drives the build-server settings screen: shows and edits the control-plane base URL, and mints /
 * displays the anonymous device token. The URL is held as a local draft and only persisted on Save
 * (so trailing-slash trimming and re-emits don't fight the user mid-type); the token is minted on
 * demand via [RemoteClient.registerDevice] and cached by [ServerSettingsRepository].
 */
class ServerSettingsViewModel(
    private val settings: ServerSettingsRepository,
    private val client: RemoteClient,
    private val gitAuthorStore: GitAuthorStore,
) : BaseViewModel<ServerSettingsUiState, Nothing>(
    initialState = ServerSettingsUiState(),
), ServerSettingsInteractionListener {

    /** Once the user edits the field, stop overwriting the draft from persisted emissions. */
    private var urlTouched = false
    private var authorTouched = false

    init {
        tryToCollect(
            block = { settings.observe() },
            onCollect = { persisted ->
                updateState {
                    copy(
                        baseUrl = if (urlTouched) baseUrl else persisted.baseUrl,
                        dirty = if (urlTouched) baseUrl.trim().trimEnd('/') != persisted.baseUrl else false,
                        deviceToken = persisted.deviceToken,
                        isRegistered = persisted.isRegistered,
                    )
                }
            },
        )
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

    override fun onBaseUrlChanged(url: String) {
        urlTouched = true
        updateState { copy(baseUrl = url, dirty = true, statusMessage = null, isError = false) }
    }

    override fun onSaveBaseUrl() {
        val url = state.value.baseUrl.trim()
        if (url.isBlank()) {
            updateState { copy(statusMessage = "Enter a server URL", isError = true) }
            return
        }
        viewModelScope.launch {
            settings.setBaseUrl(url)
            urlTouched = false
            updateState { copy(dirty = false, statusMessage = "Saved", isError = false) }
        }
    }

    override fun onRegisterDevice() {
        if (state.value.isRegistering) return
        // Persist any pending URL edit first so registration hits the intended server.
        if (state.value.dirty) onSaveBaseUrl()
        tryToExecute(
            onStart = { updateState { copy(isRegistering = true, statusMessage = null, isError = false) } },
            block = { client.registerDevice() },
            onSuccess = { updateState { copy(isRegistering = false, statusMessage = "Device registered", isError = false) } },
            onError = { e ->
                val reason = (e as? RemoteException)?.message ?: e.message ?: "Registration failed"
                updateState { copy(isRegistering = false, statusMessage = reason, isError = true) }
            },
        )
    }

    override fun onClearToken() {
        viewModelScope.launch {
            settings.clearDeviceToken()
            updateState { copy(statusMessage = "Token cleared", isError = false) }
        }
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
}
