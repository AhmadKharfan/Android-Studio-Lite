package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthController
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthMode
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthPromptActions
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthPromptState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreation
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreator
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a screen needs to offer the Cloud Build setup. [storageCreator] is null when the app can't create
 * the build storage itself.
 */
class CloudBuildServices(
    val readiness: CloudBuildReadiness,
    val access: GitHubBuildAccess,
    val storageCreator: CloudBuildStorageCreator? = null,
)

/** Where the setup was opened from: Run starts the build once ready, Settings just closes. */
enum class CloudBuildSetupOrigin { Run, Settings }

@Immutable
data class CloudBuildSetupUiState(
    val visible: Boolean = false,
    val origin: CloudBuildSetupOrigin = CloudBuildSetupOrigin.Settings,
    /** The latest settled readiness; stays on screen while a new check runs. */
    val readiness: CloudBuildState? = null,
    val checking: Boolean = false,
    /** The user went to GitHub from the setup; coming back re-checks. */
    val awaitingReturn: Boolean = false,
    val installUrl: String? = null,
    val authPrompt: GitAuthPromptState = GitAuthPromptState(),
    /** Whether the app can create the build storage itself (a GitHub sign-in that might is on this device). */
    val canCreateStorage: Boolean = false,
    val creatingStorage: Boolean = false,
    /** The last creation attempt, when it created or found the storage, or explains why it couldn't. */
    val storageCreation: CloudBuildStorageCreation? = null,
) {
    val screen: CloudBuildSetupScreen
        get() = cloudBuildSetupScreen(
            readiness,
            installUrl,
            StorageCreationOptions(
                canCreate = canCreateStorage && storageCreation.allowsRetry(),
                created = storageCreation is CloudBuildStorageCreation.Created ||
                    storageCreation is CloudBuildStorageCreation.AlreadyExists,
            ),
        )
}

/** After these, creating again from the app can't help; GitHub's own form is the way. */
private fun CloudBuildStorageCreation?.allowsRetry(): Boolean = when (this) {
    null, CloudBuildStorageCreation.Offline, CloudBuildStorageCreation.GitHubUnavailable,
    is CloudBuildStorageCreation.Failed, CloudBuildStorageCreation.BuildNotConnected,
    -> true
    else -> false
}

interface CloudBuildSetupActions : GitAuthPromptActions {
    fun onCloudBuildConnect()
    fun onCloudBuildCreateStorage()
    fun onCloudBuildSwitchAccount()
    fun onCloudBuildOpenedGitHub()
    fun onCloudBuildCheckAgain()
    fun onCloudBuildDismiss()
}

/**
 * Drives the Cloud Build setup from the shared [CloudBuildReadiness]: it never decides readiness
 * itself, it asks and shows the answer.
 *
 * Opened from Run, it hands control back through `onReady` only when a check it ran turns Ready,
 * never when the first answer already is (then the blocker was something else and running again
 * would loop).
 */
class CloudBuildSetupController(
    private val scope: CoroutineScope,
    private val readiness: CloudBuildReadiness,
    private val access: GitHubBuildAccess,
    private val storageCreator: CloudBuildStorageCreator? = null,
    private val emit: (CloudBuildSetupUiState) -> Unit,
) : CloudBuildSetupActions {

    constructor(scope: CoroutineScope, services: CloudBuildServices, emit: (CloudBuildSetupUiState) -> Unit) :
        this(scope, services.readiness, services.access, services.storageCreator, emit)

    private var current = CloudBuildSetupUiState(
        installUrl = access.installUrl,
        canCreateStorage = storageCreator?.isAvailable == true,
    )
    private var onReady: (() -> Unit)? = null
    private var armed = false

    private val auth = GitAuthController(scope, access.credentials, access.authenticator) { prompt ->
        set { copy(authPrompt = prompt) }
    }

    init {
        scope.launch {
            readiness.state.collect { state ->
                if (state == CloudBuildState.Checking) {
                    set { copy(checking = true) }
                } else {
                    set { copy(readiness = state ?: readiness, checking = false) }
                    if (state != null) settle(state)
                }
            }
        }
    }

    /** Opens the setup and checks again. From Run, [onReady] runs once it's ready. */
    fun open(origin: CloudBuildSetupOrigin, onReady: (() -> Unit)? = null) {
        this.onReady = onReady
        armed = false
        set {
            copy(
                visible = true,
                origin = origin,
                awaitingReturn = false,
                canCreateStorage = storageCreator?.isAvailable == true,
                storageCreation = null,
            )
        }
        scope.launch {
            val first = readiness.check()
            if (first is CloudBuildState.Ready && origin == CloudBuildSetupOrigin.Run) {
                // Cloud Build was fine all along: whatever blocked Run wasn't setup.
                close()
            } else {
                armed = true
                settle(first)
            }
        }
    }

    /** Checks again, e.g. when settings open, without showing the setup. */
    fun refresh() {
        scope.launch { readiness.check() }
    }

    /** The screen became visible again, e.g. back from GitHub. */
    fun onResumed() {
        if (current.visible && current.awaitingReturn) {
            set { copy(awaitingReturn = false) }
            refresh()
        }
    }

    override fun onCloudBuildConnect() = auth.open(GITHUB_HOST) { refresh() }

    /** Creates the private build storage from the app, then checks again: allowing access may be next. */
    override fun onCloudBuildCreateStorage() {
        val creator = storageCreator ?: return
        if (current.creatingStorage) return
        set { copy(creatingStorage = true, storageCreation = null) }
        scope.launch {
            val outcome = creator.create()
            set { copy(creatingStorage = false, storageCreation = outcome) }
            if (outcome is CloudBuildStorageCreation.Created || outcome is CloudBuildStorageCreation.AlreadyExists) {
                armed = true
                settle(readiness.check())
            }
        }
    }

    override fun onCloudBuildSwitchAccount() {
        access.credentials.clear(GITHUB_HOST)
        onCloudBuildConnect()
    }

    override fun onCloudBuildOpenedGitHub() = set { copy(awaitingReturn = true) }

    override fun onCloudBuildCheckAgain() = refresh()

    override fun onCloudBuildDismiss() = close()

    override fun onAuthModeChanged(mode: GitAuthMode) = auth.onAuthModeChanged(mode)
    override fun onAuthTokenChanged(token: String) = auth.onAuthTokenChanged(token)
    override fun onSubmitAuthToken() = auth.onSubmitAuthToken()
    override fun onStartGitHubSignIn() = auth.onStartGitHubSignIn()
    override fun onDismissAuthPrompt() = auth.onDismissAuthPrompt()

    private fun settle(state: CloudBuildState) {
        if (state !is CloudBuildState.Ready || !armed || !current.visible) return
        if (current.origin == CloudBuildSetupOrigin.Run) {
            val ready = onReady
            close()
            ready?.invoke()
        }
    }

    private fun close() {
        onReady = null
        armed = false
        set { copy(visible = false, awaitingReturn = false) }
    }

    private fun set(transform: CloudBuildSetupUiState.() -> CloudBuildSetupUiState) {
        current = transform(current)
        emit(current)
    }

    private companion object {
        const val GITHUB_HOST = "github.com"
    }
}
