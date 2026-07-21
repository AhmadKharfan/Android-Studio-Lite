package com.ahmadkharfan.androidstudiolite.feature.editor.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GitAuthMode { SignIn, Token }

@Immutable
data class GitHubDeviceCodeUi(val userCode: String, val verificationUri: String)

@Immutable
data class GitAuthPromptState(
    val visible: Boolean = false,
    val host: String? = null,
    val gitHubAvailable: Boolean = false,
    val mode: GitAuthMode = GitAuthMode.SignIn,
    val token: String = "",
    val isBusy: Boolean = false,
    val device: GitHubDeviceCodeUi? = null,
    val succeeded: Boolean = false,
    val error: String? = null,
)

interface GitAuthPromptActions {
    fun onAuthModeChanged(mode: GitAuthMode)
    fun onAuthTokenChanged(token: String)
    fun onSubmitAuthToken()
    fun onStartGitHubSignIn()
    fun onDismissAuthPrompt()
}

class GitAuthController(
    private val scope: CoroutineScope,
    private val credentialStore: GitCredentialStore,
    private val authenticator: GitHubDeviceAuthenticator,
    private val emit: (GitAuthPromptState) -> Unit,
) : GitAuthPromptActions {

    private var current = GitAuthPromptState(gitHubAvailable = authenticator.isConfigured)
    private var pendingRetry: (() -> Unit)? = null
    private var deviceJob: Job? = null
    private var closeJob: Job? = null

    val isGitHubSignInAvailable: Boolean get() = authenticator.isConfigured

    fun hasCredentials(host: String?): Boolean = host != null && credentialStore.hasCredentials(host)

    fun open(host: String?, retry: () -> Unit) {
        pendingRetry = retry
        cancelJobs()
        set {
            GitAuthPromptState(
                visible = true,
                host = host,
                gitHubAvailable = authenticator.isConfigured,
                mode = if (authenticator.isConfigured) GitAuthMode.SignIn else GitAuthMode.Token,
            )
        }
    }

    override fun onAuthModeChanged(mode: GitAuthMode) {
        if (mode != GitAuthMode.SignIn) deviceJob?.cancel().also { deviceJob = null }
        set { copy(mode = mode, error = null, device = null, isBusy = false, succeeded = false) }
    }

    override fun onAuthTokenChanged(token: String) = set { copy(token = token, error = null) }

    override fun onSubmitAuthToken() {
        val token = current.token.trim()
        if (token.isBlank()) {
            set { copy(error = "Enter an access token") }
            return
        }
        current.host?.let { credentialStore.save(it, GitCredentials(username = "", token = token)) }
        closeAndRetry()
    }

    override fun onStartGitHubSignIn() {
        if (!authenticator.isConfigured) {
            set { copy(mode = GitAuthMode.Token, error = "GitHub sign-in isn't configured on this build. Use an access token.") }
            return
        }


        if (current.isBusy || current.device != null || current.succeeded) return
        deviceJob?.cancel()
        deviceJob = scope.launch {
            authenticator.authenticate().collect { step ->
                when (step) {
                    GitHubDeviceAuthState.RequestingCode -> set { copy(isBusy = true, error = null, device = null, succeeded = false) }
                    is GitHubDeviceAuthState.AwaitingAuthorization ->
                        set { copy(isBusy = true, error = null, device = GitHubDeviceCodeUi(step.userCode, step.verificationUri)) }
                    is GitHubDeviceAuthState.Success -> onAuthorized()
                    is GitHubDeviceAuthState.Error -> set { copy(isBusy = false, device = null, error = step.message) }
                }
            }
        }
    }

    override fun onDismissAuthPrompt() {


        if (current.succeeded) {
            closeAndRetry()
            return
        }
        cancelJobs()
        pendingRetry = null
        set { GitAuthPromptState(gitHubAvailable = authenticator.isConfigured) }
    }

    private fun onAuthorized() {
        deviceJob?.cancel()
        deviceJob = null
        set { copy(isBusy = false, device = null, succeeded = true, error = null) }
        closeJob?.cancel()
        closeJob = scope.launch {
            delay(1100)
            closeAndRetry()
        }
    }

    private fun closeAndRetry() {
        cancelJobs()
        val retry = pendingRetry
        pendingRetry = null
        set { GitAuthPromptState(gitHubAvailable = authenticator.isConfigured) }
        retry?.invoke()
    }

    private fun cancelJobs() {
        deviceJob?.cancel()
        deviceJob = null
        closeJob?.cancel()
        closeJob = null
    }

    private fun set(block: GitAuthPromptState.() -> GitAuthPromptState) {
        current = current.block()
        emit(current)
    }
}

@Composable
fun GitHubAuthDialog(state: GitAuthPromptState, actions: GitAuthPromptActions) {
    if (!state.visible) return
    val colors = AslTheme.colors
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var codeCopied by remember(state.device?.userCode) { mutableStateOf(false) }
    val host = state.host ?: "the remote"
    val confirmLabel = when {
        state.succeeded -> "Done"
        state.mode == GitAuthMode.Token -> "Save & retry"
        state.isBusy || state.device != null -> "Waiting…"
        else -> "Sign in"
    }
    AslDialog(
        title = if (state.succeeded) "Connected" else "Sign in to $host",
        variant = AslDialogVariant.Input,
        confirmLabel = confirmLabel,
        cancelLabel = if (state.succeeded) null else "Cancel",
        onDismiss = actions::onDismissAuthPrompt,
        onConfirm = {
            when {
                state.succeeded -> actions.onDismissAuthPrompt()
                state.mode == GitAuthMode.Token -> actions.onSubmitAuthToken()


                else -> actions.onStartGitHubSignIn()
            }
        },
        inputContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.succeeded) {
                    Text(
                        text = "Connected to GitHub",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.success,
                    )
                    Text(
                        text = "You're signed in. This credential is saved and reused across all your projects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    return@Column
                }
                if (state.gitHubAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AslChip(
                            label = "GitHub account",
                            kind = AslChipKind.Filter,
                            selected = state.mode == GitAuthMode.SignIn,
                            onClick = { actions.onAuthModeChanged(GitAuthMode.SignIn) },
                        )
                        AslChip(
                            label = "Access token",
                            kind = AslChipKind.Filter,
                            selected = state.mode == GitAuthMode.Token,
                            onClick = { actions.onAuthModeChanged(GitAuthMode.Token) },
                        )
                    }
                }

                if (state.mode == GitAuthMode.SignIn && state.gitHubAvailable) {
                    val device = state.device
                    if (device == null) {
                        Text(
                            "Sign in with your GitHub account. We'll show you a short code to enter on " +
                                "github.com; the resulting access is stored securely and reused for every project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        Text(
                            "Tap the code to copy it, then open GitHub and paste it:",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = device.userCode,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceContainerLow, AslShape.md)
                                .clickable {
                                    clipboard.setText(AnnotatedString(device.userCode))
                                    codeCopied = true
                                }
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            text = if (codeCopied) "Copied to clipboard" else "Tap to copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (codeCopied) colors.success else colors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AslButton(
                            label = "Open GitHub",
                            onClick = { uriHandler.openUri(device.verificationUri) },
                            icon = "external-link",
                            variant = AslButtonVariant.Secondary,
                            fullWidth = true,
                        )
                        Text(
                            "Waiting for you to authorize on GitHub…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                        )
                    }
                } else {
                    Text(
                        "Pushing to $host needs a GitHub personal access token (classic or fine-grained) " +
                            "with repository write access. It's stored securely and reused for future push/pull.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    AslTextField(
                        value = state.token,
                        onValueChange = actions::onAuthTokenChanged,
                        label = "Access token",
                        placeholder = "ghp_…",
                        type = AslTextFieldType.Password,
                    )
                    AslButton(
                        label = "Create a token on GitHub",
                        onClick = {
                            uriHandler.openUri(
                                "https://github.com/settings/tokens/new?scopes=repo&description=Android%20Studio%20Lite",
                            )
                        },
                        icon = "external-link",
                        variant = AslButtonVariant.Tertiary,
                    )
                }

                state.error?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        },
    )
}
