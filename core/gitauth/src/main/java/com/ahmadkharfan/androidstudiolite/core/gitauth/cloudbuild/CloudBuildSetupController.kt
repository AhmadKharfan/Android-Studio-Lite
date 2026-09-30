package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthController
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthMode
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthPromptActions
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthPromptState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
) {
    val screen: CloudBuildSetupScreen get() = cloudBuildSetupScreen(readiness, installUrl)
}

interface CloudBuildSetupActions : GitAuthPromptActions {
    fun onCloudBuildConnect()
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
    private val emit: (CloudBuildSetupUiState) -> Unit,
) : CloudBuildSetupActions {

    private var current = CloudBuildSetupUiState(installUrl = access.installUrl)
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
        set { copy(visible = true, origin = origin, awaitingReturn = false) }
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
