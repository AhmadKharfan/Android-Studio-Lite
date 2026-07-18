package com.ahmadkharfan.androidstudiolite.feature.settings.gitauth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import org.koin.androidx.compose.koinViewModel

@Composable
fun GitAuthSettingsRoute(
    onBack: () -> Unit,
    viewModel: GitAuthSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitAuthSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun GitAuthSettingsScreen(
    uiState: GitAuthSettingsUiState,
    interactionListener: GitAuthSettingsInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(title = "Git & GitHub", subtitle = "Sign-in, tokens, author", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HubSectionHeader("GitHub account")
                GitHubAccountCard(uiState = uiState, interactionListener = interactionListener)

                Spacer(Modifier.height(20.dp))
                HubSectionHeader("Git author")
                Text(
                    text = "Used as the author and committer of new commits across all projects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                AslTextField(
                    value = uiState.gitAuthorName,
                    onValueChange = interactionListener::onGitAuthorNameChanged,
                    label = "Name",
                    placeholder = "Your name",
                )
                Spacer(Modifier.height(10.dp))
                AslTextField(
                    value = uiState.gitAuthorEmail,
                    onValueChange = interactionListener::onGitAuthorEmailChanged,
                    label = "Email",
                    placeholder = "you@example.com",
                )
                Spacer(Modifier.height(10.dp))
                AslButton(
                    label = "Save Git author",
                    onClick = interactionListener::onSaveGitAuthor,
                    disabled = !uiState.gitAuthorDirty,
                )

                if (uiState.statusMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isError) colors.error else colors.success,
                    )
                }
            }
        }
    }

    GitHubAuthDialog(uiState.authPrompt, interactionListener)
}

@Composable
private fun GitHubAccountCard(
    uiState: GitAuthSettingsUiState,
    interactionListener: GitAuthSettingsInteractionListener,
) {
    val colors = AslTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(16.dp),
    ) {
        Text(
            text = if (uiState.gitHubConnected) "Connected" else "Not connected",
            style = MaterialTheme.typography.titleSmall,
            color = if (uiState.gitHubConnected) colors.success else colors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (uiState.gitHubConnected) {
                "A GitHub credential is saved and reused for push/pull in every project."
            } else if (uiState.gitHubAvailable) {
                "Sign in with your GitHub account or add an access token. Stored securely and shared across projects."
            } else {
                "Add a GitHub personal access token. Stored securely and shared across projects."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AslButton(
                label = when {
                    uiState.gitHubConnected -> "Update credential"
                    uiState.gitHubAvailable -> "Sign in with GitHub"
                    else -> "Add access token"
                },
                icon = if (uiState.gitHubAvailable && !uiState.gitHubConnected) "github" else "key-round",
                onClick = interactionListener::onConnectGitHub,
            )
            if (uiState.gitHubConnected) {
                AslButton(
                    label = "Sign out",
                    onClick = interactionListener::onDisconnectGitHub,
                    variant = AslButtonVariant.Secondary,
                )
            }
        }
    }
}
