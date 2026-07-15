package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlinx.coroutines.launch

class BuildRunViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val keystoreManager: KeystoreManager,
) : BaseViewModel<BuildRunUiState, Nothing>(initialState = BuildRunUiState()), BuildRunInteractionListener {

    init {
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                updateState {
                    copy(
                        launchAfterInstall = prefs.launchAfterInstall,
                        installViaShizuku = prefs.installViaShizuku,
                        buildOutputAab = prefs.buildOutputAab,
                        preferGitSource = prefs.preferGitSource,
                    )
                }
            },
        )
        updateState {
            copy(
                debugKeystorePath = keystoreManager.debugKeystoreFile().absolutePath,
                suggestedReleaseKeystorePath = keystoreManager.suggestedReleaseKeystoreFile().absolutePath,
            )
        }
        refreshReleaseKeystore()
    }

    private fun refreshReleaseKeystore() {
        viewModelScope.launch {
            val config = keystoreManager.releaseSigningConfig()
            updateState { copy(releaseKeystoreSummary = config?.summary()) }
        }
    }

    override fun onToggleLaunchAfterInstall(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(launchAfterInstall = enabled) } }
    }

    override fun onToggleInstallViaShizuku(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(installViaShizuku = enabled) } }
    }

    override fun onToggleAabOutput(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(buildOutputAab = enabled) } }
    }

    override fun onTogglePreferGitSource(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(preferGitSource = enabled) } }
    }

    override fun onOpenKeystoreDialog(mode: KeystoreDialogMode) {
        updateState { copy(keystoreDialog = mode, keystoreError = null) }
    }

    override fun onDismissKeystoreDialog() {
        updateState { copy(keystoreDialog = null, keystoreError = null, keystoreBusy = false) }
    }

    override fun onCreateReleaseKeystore(form: KeystoreForm) {
        runKeystoreOp {
            keystoreManager.createReleaseKeystore(
                ReleaseKeystoreParams(
                    storeFile = File(form.storePath),
                    storePassword = form.storePassword,
                    keyAlias = form.keyAlias,
                    keyPassword = form.keyPassword,
                    validityYears = form.validityYears.toIntOrNull() ?: 25,
                    commonName = form.commonName,
                    organization = form.organization,
                    country = form.country,
                ),
            )
        }
    }

    override fun onImportReleaseKeystore(form: KeystoreForm) {
        runKeystoreOp {
            keystoreManager.importReleaseKeystore(
                storeFile = File(form.storePath),
                storePassword = form.storePassword,
                keyAlias = form.keyAlias,
                keyPassword = form.keyPassword,
            )
        }
    }

    override fun onRemoveReleaseKeystore() {
        viewModelScope.launch {
            keystoreManager.clearReleaseKeystore()
            updateState { copy(releaseKeystoreSummary = null, message = "Release keystore removed") }
        }
    }

    override fun onMessageShown() {
        updateState { copy(message = null) }
    }

    /** Runs a keystore create/import op, mapping success/failure into UI state. */
    private fun runKeystoreOp(op: suspend () -> SigningConfig) {
        updateState { copy(keystoreBusy = true, keystoreError = null) }
        viewModelScope.launch {
            try {
                val config = op()
                updateState {
                    copy(
                        keystoreBusy = false,
                        keystoreDialog = null,
                        releaseKeystoreSummary = config.summary(),
                        message = "Release keystore ready",
                    )
                }
            } catch (e: KeystoreException) {
                updateState { copy(keystoreBusy = false, keystoreError = e.error.toMessage()) }
            } catch (e: Throwable) {
                updateState { copy(keystoreBusy = false, keystoreError = e.message ?: "Keystore operation failed") }
            }
        }
    }

    private fun SigningConfig.summary(): String = "${storeFile.name} · $keyAlias"

    private fun KeystoreError.toMessage(): String = when (this) {
        is KeystoreError.InvalidParams -> reason
        KeystoreError.FileNotFound -> "Keystore file not found"
        KeystoreError.WrongStorePassword -> "Wrong keystore password"
        is KeystoreError.AliasNotFound -> "Alias not found. Available: ${availableAliases.joinToString()}"
        KeystoreError.WrongKeyPassword -> "Wrong key password"
        is KeystoreError.Io -> message
    }
}
