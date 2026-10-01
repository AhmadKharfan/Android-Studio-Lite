package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthMode
import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildServices
import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildSetupController
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildProviderCatalog
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildProviderIds
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
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
    private val providerCatalog: BuildProviderCatalog,
    private val gitHubBuildAccess: GitHubBuildAccess,
    cloudBuildServices: CloudBuildServices,
    context: Context,
) : BaseViewModel<BuildRunUiState, Nothing>(initialState = BuildRunUiState()), BuildRunInteractionListener {

    private val applicationContext = context.applicationContext

    private val cloudBuild = CloudBuildSetupController(viewModelScope, cloudBuildServices) { setup ->
        updateState { copy(cloudBuild = setup) }
    }

    init {
        if (gitHubBuildAccess.usesGitHubApp) {
            refreshBuildAccess()
            tryToCollect(block = { gitHubBuildAccess.credentials.changes }, onCollect = { refreshBuildAccess() })
        }
        tryToCollect(
            block = { preferencesRepository.observePreferences() },
            onCollect = { prefs ->
                val provider = providerCatalog.effective(prefs.buildProviderId)
                val firstCloudBuildView = provider == BuildProviderIds.GITHUB_ACTIONS &&
                    state.value.selectedBuildProvider != provider
                updateState {
                    copy(
                        launchAfterInstall = prefs.launchAfterInstall,
                        buildOutputAab = prefs.buildOutputAab,
                        buildProviders = providerCatalog.available,
                        selectedBuildProvider = provider,
                    )
                }
                if (firstCloudBuildView) cloudBuild.refresh()
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

    private fun refreshBuildAccess() = updateState {
        copy(
            buildAccess = BuildAccessUiState(
                connected = gitHubBuildAccess.credentials.hasCredentials(GITHUB_HOST),
                installUrl = gitHubBuildAccess.installUrl ?: GITHUB_INSTALLATIONS_URL,
            ),
        )
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

    override fun onToggleAabOutput(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.update { it.copy(buildOutputAab = enabled) } }
    }

    override fun onSelectBuildProvider(providerId: String) {
        viewModelScope.launch { preferencesRepository.update { it.copy(buildProviderId = providerId) } }
    }

    /** Settings are showing again (opened, or back from GitHub): refresh Cloud Build's status. */
    override fun onScreenResumed() {
        if (state.value.selectedBuildProvider == BuildProviderIds.GITHUB_ACTIONS) cloudBuild.refresh()
    }

    override fun onConnectBuildAccess(): Unit = cloudBuild.onCloudBuildConnect()

    override fun onDisconnectBuildAccess() {
        gitHubBuildAccess.credentials.clear(GITHUB_HOST)
        updateState { copy(message = "GitHub disconnected from builds") }
        refreshBuildAccess()
    }

    override fun onCloudBuildConnect(): Unit = cloudBuild.onCloudBuildConnect()
    override fun onCloudBuildCreateStorage(): Unit = cloudBuild.onCloudBuildCreateStorage()
    override fun onCloudBuildSwitchAccount(): Unit = cloudBuild.onCloudBuildSwitchAccount()
    override fun onCloudBuildOpenedGitHub(): Unit = cloudBuild.onCloudBuildOpenedGitHub()
    override fun onCloudBuildCheckAgain(): Unit = cloudBuild.onCloudBuildCheckAgain()
    override fun onCloudBuildDismiss(): Unit = cloudBuild.onCloudBuildDismiss()

    override fun onAuthModeChanged(mode: GitAuthMode): Unit = cloudBuild.onAuthModeChanged(mode)
    override fun onAuthTokenChanged(token: String): Unit = cloudBuild.onAuthTokenChanged(token)
    override fun onSubmitAuthToken(): Unit = cloudBuild.onSubmitAuthToken()
    override fun onStartGitHubSignIn(): Unit = cloudBuild.onStartGitHubSignIn()
    override fun onDismissAuthPrompt(): Unit = cloudBuild.onDismissAuthPrompt()

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
            val source = if (form.storePath.startsWith("content://")) {
                val target = File(applicationContext.cacheDir, "keystore-import-${java.util.UUID.randomUUID()}")
                applicationContext.contentResolver.openInputStream(Uri.parse(form.storePath))?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: throw KeystoreException(KeystoreError.FileNotFound)
                target
            } else {
                File(form.storePath)
            }
            keystoreManager.importReleaseKeystore(
                storeFile = source,
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

    private companion object {
        const val GITHUB_HOST = "github.com"
        const val GITHUB_INSTALLATIONS_URL = "https://github.com/settings/installations"
    }
}
